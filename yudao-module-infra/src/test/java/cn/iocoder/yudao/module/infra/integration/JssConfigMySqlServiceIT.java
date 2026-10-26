package cn.iocoder.yudao.module.infra.integration;

import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.infra.controller.admin.config.ConfigController;
import cn.iocoder.yudao.module.infra.controller.admin.config.vo.ConfigPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.config.vo.ConfigSaveReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.config.ConfigDO;
import cn.iocoder.yudao.module.infra.dal.mysql.config.ConfigMapper;
import cn.iocoder.yudao.module.infra.service.config.ConfigService;
import cn.iocoder.yudao.module.infra.service.config.ConfigServiceImpl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

import javax.sql.DataSource;

/** Actual MySQL, service, mapper, tenant/pagination SQL and transactions; no HTTP or Redis. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_config_service_verify_[0-9a-f]{12}.*")
class JssConfigMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static ConfigService service;
    private static ConfigMapper mapper;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(ConfigService.class);
        mapper = context.getBean(ConfigMapper.class);
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_config_service_verify_[0-9a-f]{12}"));
        }
    }

    @BeforeEach
    void tenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({ConfigServiceImpl.class, ConfigController.class})
    static class Config {
        @Bean
        DataSource dataSource() {
            return new UnpooledDataSource(
                    "com.mysql.cj.jdbc.Driver",
                    System.getenv("JSS_TEST_MYSQL_URL"),
                    System.getenv("JSS_TEST_MYSQL_USER"),
                    System.getenv("JSS_TEST_MYSQL_PASSWORD"));
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource source) {
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(
                    new Environment(
                            "jss-config-services", new SpringManagedTransactionFactory(), source));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
            GlobalConfigUtils.setGlobalConfig(
                    configuration,
                    new GlobalConfig()
                            .setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO))
                            .setMetaObjectHandler(new DefaultDBFieldHandler())
                            .setSqlInjector(new YudaoSqlInjector()));
            var plugins = new MybatisPlusInterceptor();
            plugins.addInnerInterceptor(
                    new TenantLineInnerInterceptor(
                            new TenantDatabaseInterceptor(new TenantProperties())));
            plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            configuration.addInterceptor(plugins);
            configuration.addMapper(ConfigMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        ConfigMapper configMapper(SqlSessionTemplate sql) {
            return sql.getMapper(ConfigMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    private String unique() {
        return "JSSConfig" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private ConfigSaveReqVO form(String key) {
        return new ConfigSaveReqVO()
                .setCategory("jss-test")
                .setName(key)
                .setKey(key)
                .setValue("0009007199254740993")
                .setVisible(true)
                .setRemark("original");
    }

    private long create(String key) {
        return service.createConfig(form(key));
    }

    private void error(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ServiceException.class, action).getCode());
    }

    @Test
    void crudKeepsBooleanFalseStringValuesAndRenameClearsTheOriginalKey() {
        String key = unique();
        long id = create(key);
        assertEquals(2, service.getConfig(id).getType());
        service.updateConfig(
                form(key + "Renamed").setId(id).setValue("0").setVisible(false).setRemark(""));
        var saved = service.getConfig(id);
        assertEquals("0", saved.getValue());
        assertFalse(saved.getVisible());
        assertEquals("", saved.getRemark());
        assertEquals(2, saved.getType());
        assertNull(service.getConfigByKey(key));
        assertEquals(id, service.getConfigByKey(key + "Renamed").getId());
        assertNotNull(create(key));
        error(
                CONFIG_KEY_DUPLICATE.getCode(),
                () -> create((key + "Renamed").toLowerCase(Locale.ROOT)));
        service.deleteConfig(id);
        assertNull(service.getConfig(id));
        error(CONFIG_NOT_EXISTS.getCode(), () -> service.updateConfig(form(unique()).setId(id)));
    }

    @Test
    void paginationCombinesNameKeyTypeAndDatesWithoutIncludingOlderRows() {
        String key = unique();
        long old = create(key + "Old");
        long fresh = create(key + "Fresh");
        long builtin = create(key + "System");
        var update = new ConfigDO().setId(old);
        update.setCreateTime(LocalDateTime.now().minusDays(2));
        mapper.updateById(update);
        mapper.updateById(new ConfigDO().setId(builtin).setType(1));
        var query =
                new ConfigPageReqVO()
                        .setName(key)
                        .setKey("Fresh")
                        .setType(2)
                        .setCreateTime(
                                new LocalDateTime[] {
                                    LocalDateTime.now().minusHours(1),
                                    LocalDateTime.now().plusHours(1)
                                });
        query.setPageNo(1);
        query.setPageSize(20);
        assertEquals(
                List.of(fresh),
                service.getConfigPage(query).getList().stream().map(ConfigDO::getId).toList());
        query.setKey(null).setType(null).setCreateTime(null);
        query.setPageSize(2);
        assertEquals(3, service.getConfigPage(query).getTotal());
        assertEquals(2, service.getConfigPage(query).getList().size());
        query.setPageNo(2);
        assertEquals(old, service.getConfigPage(query).getList().getFirst().getId());
    }

    @Test
    void builtinsStayProtectedAcrossSingleAndMixedBatchDeletionButRemainEditable() {
        String key = unique();
        long custom = create(key + "Custom");
        long builtin = create(key + "Builtin");
        mapper.updateById(new ConfigDO().setId(builtin).setType(1));
        error(CONFIG_CAN_NOT_DELETE_SYSTEM_TYPE.getCode(), () -> service.deleteConfig(builtin));
        error(
                CONFIG_CAN_NOT_DELETE_SYSTEM_TYPE.getCode(),
                () -> service.deleteConfigList(List.of(custom, builtin)));
        assertNotNull(service.getConfig(custom));
        assertNotNull(service.getConfig(builtin));
        service.updateConfig(form(key + "Builtin").setId(builtin).setVisible(false));
        assertEquals(1, service.getConfig(builtin).getType());
    }

    @Test
    void missingTargetRejectsTheWholeBatchWithoutDeletingTheExistingRow() {
        long id = create(unique());
        assertAll(
                () ->
                        error(
                                CONFIG_NOT_EXISTS.getCode(),
                                () -> service.deleteConfigList(List.of(id, Long.MAX_VALUE))),
                () ->
                        assertNotNull(
                                service.getConfig(id),
                                "Mixed missing config batch deleted an existing row"));
    }

    @Test
    void sharedScopeAndDirectControllerVisibilityUseProductionRowsWithoutHttpSecurity() {
        String key = unique();
        long id = create(key);
        TenantContextHolder.setTenantId(2L);
        assertEquals(id, service.getConfigByKey(key).getId());
        error(CONFIG_KEY_DUPLICATE.getCode(), () -> create(key));
        var controller = context.getBean(ConfigController.class);
        assertNull(controller.getConfigKey(unique()).getData());
        assertEquals("0009007199254740993", controller.getConfigKey(key).getData());
        service.updateConfig(form(key).setId(id).setVisible(false));
        error(CONFIG_GET_VALUE_ERROR_IF_VISIBLE.getCode(), () -> controller.getConfigKey(key));
        assertEquals("0009007199254740993", service.getConfig(id).getValue());
    }

    @Test
    void duplicateIdsAreAcceptedAndBatchesJoinTheCallersRollback() {
        long a = create(unique());
        long b = create(unique());
        var transaction =
                new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                status -> {
                                    service.deleteConfigList(List.of(a, b, a));
                                    assertNull(service.getConfig(a));
                                    throw new IllegalStateException("owned rollback");
                                }));
        assertNotNull(service.getConfig(a));
        assertNotNull(service.getConfig(b));
        service.deleteConfigList(List.of(a, b, a));
        assertNull(service.getConfig(a));
        assertNull(service.getConfig(b));
    }
}
