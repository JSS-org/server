package cn.iocoder.yudao.module.system.integration;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.controller.admin.dict.vo.data.DictDataPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dict.vo.data.DictDataSaveReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dict.vo.type.DictTypePageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dict.vo.type.DictTypeSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dict.DictDataDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dict.DictTypeDO;
import cn.iocoder.yudao.module.system.dal.mysql.dict.DictDataMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dict.DictTypeMapper;
import cn.iocoder.yudao.module.system.service.dict.DictDataService;
import cn.iocoder.yudao.module.system.service.dict.DictDataServiceImpl;
import cn.iocoder.yudao.module.system.service.dict.DictTypeService;
import cn.iocoder.yudao.module.system.service.dict.DictTypeServiceImpl;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

import javax.sql.DataSource;

/** Shared dictionaries on production MySQL DDL, SQL and transactions; no HTTP or doubles. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_dict_service_verify_[0-9a-f]{12}.*")
class JssDictMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static DictTypeService types;
    private static DictDataService values;
    private static DictTypeMapper typeMapper;
    private static DictDataMapper dataMapper;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        types = context.getBean(DictTypeService.class);
        values = context.getBean(DictDataService.class);
        typeMapper = context.getBean(DictTypeMapper.class);
        dataMapper = context.getBean(DictDataMapper.class);
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_dict_service_verify_[0-9a-f]{12}"));
        }
    }

    private String unique() {
        return "JSSDict" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private DictTypeSaveReqVO typeForm(String key) {
        return new DictTypeSaveReqVO().setName(key).setType(key).setStatus(0).setRemark("original");
    }

    private DictDataSaveReqVO dataForm(String key, String value, int sort) {
        return new DictDataSaveReqVO()
                .setDictType(key)
                .setLabel("label" + value)
                .setValue(value)
                .setSort(sort)
                .setStatus(0)
                .setColorType("warning")
                .setCssClass("original-class")
                .setRemark("original");
    }

    private DictTypePageReqVO typeQuery(String key) {
        var query = new DictTypePageReqVO().setName(key);
        query.setPageNo(1);
        query.setPageSize(20);
        return query;
    }

    private DictDataPageReqVO dataQuery(String key) {
        var query = new DictDataPageReqVO().setDictType(key);
        query.setPageNo(1);
        query.setPageSize(20);
        return query;
    }

    private void error(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ServiceException.class, action).getCode());
    }

    @Test
    void typeCrudPaginationAndDeletedNamesCanBeReused() {
        String key = unique();
        long a = types.createDictType(typeForm(key + "A"));
        long b = types.createDictType(typeForm(key + "B"));
        var form = typeForm(key + "B").setId(b).setStatus(1).setRemark("");
        types.updateDictType(form);
        assertEquals("", types.getDictType(b).getRemark());
        var query =
                typeQuery(key)
                        .setType("B")
                        .setStatus(1)
                        .setCreateTime(
                                new LocalDateTime[] {
                                    LocalDateTime.now().minusHours(1),
                                    LocalDateTime.now().plusHours(1)
                                });
        assertEquals(
                List.of(b),
                types.getDictTypePage(query).getList().stream().map(DictTypeDO::getId).toList());
        var all = typeQuery(key);
        all.setPageSize(1);
        assertEquals(2, types.getDictTypePage(all).getTotal());
        assertEquals(b, types.getDictTypePage(all).getList().getFirst().getId());
        all.setPageNo(2);
        assertEquals(a, types.getDictTypePage(all).getList().getFirst().getId());
        error(
                DICT_TYPE_NAME_DUPLICATE.getCode(),
                () -> types.createDictType(typeForm(key + "A").setType(unique())));
        error(
                DICT_TYPE_TYPE_DUPLICATE.getCode(),
                () -> types.createDictType(typeForm(key + "A").setName(unique())));
        types.deleteDictType(a);
        assertNull(types.getDictType(a));
        assertNotNull(types.createDictType(typeForm(key + "A")));
    }

    @Test
    void dataCrudFilteringSortingAndFullFormClearing() {
        String key = unique();
        types.createDictType(typeForm(key));
        long a = values.createDictData(dataForm(key, "0", 1));
        long b = values.createDictData(dataForm(key, "second", 8));
        values.updateDictData(
                dataForm(key, "0", 1)
                        .setId(a)
                        .setStatus(1)
                        .setRemark("")
                        .setColorType("")
                        .setCssClass(""));
        var saved = values.getDictData(a);
        assertEquals("0", saved.getValue());
        assertEquals("", saved.getRemark());
        assertEquals("", saved.getColorType());
        assertEquals("", saved.getCssClass());
        var query = dataQuery(key).setLabel("label").setStatus(1);
        assertEquals(
                List.of(a),
                values.getDictDataPage(query).getList().stream().map(DictDataDO::getId).toList());
        assertEquals(
                List.of(b, a),
                values.getDictDataPage(dataQuery(key)).getList().stream()
                        .map(DictDataDO::getId)
                        .toList());
        assertEquals(
                List.of(b),
                values.getDictDataList(0, key).stream().map(DictDataDO::getId).toList());
        error(
                DICT_DATA_VALUE_DUPLICATE.getCode(),
                () -> values.createDictData(dataForm(key, "0", 2)));
        String other = unique();
        types.createDictType(typeForm(other));
        assertNotNull(values.createDictData(dataForm(other, "0", 1)));
        values.deleteDictDataList(List.of(a, b, a));
        assertNull(values.getDictData(a));
        assertNull(values.getDictData(b));
    }

    @Test
    void dictionaryScopeIsSharedAndDisabledTypesAndChildrenRemainProtected() {
        String key = unique();
        long type = types.createDictType(typeForm(key));
        long data = values.createDictData(dataForm(key, "0", 1));
        TenantContextHolder.setTenantId(2L);
        assertEquals(key, types.getDictType(type).getType());
        assertEquals(key, values.getDictData(data).getDictType());
        error(
                DICT_TYPE_TYPE_DUPLICATE.getCode(),
                () -> types.createDictType(typeForm(key).setName(unique())));
        error(DICT_TYPE_HAS_CHILDREN.getCode(), () -> types.deleteDictType(type));
        types.updateDictType(typeForm(key).setId(type).setStatus(1));
        error(DICT_TYPE_NOT_ENABLE.getCode(), () -> values.createDictData(dataForm(key, "new", 2)));
        error(
                DICT_TYPE_NOT_ENABLE.getCode(),
                () -> values.updateDictData(dataForm(key, "0", 1).setId(data)));
        values.deleteDictData(data);
        types.deleteDictType(type);
        assertNull(types.getDictType(type));
    }

    @Test
    void missingTypeInBatchRejectsWithoutDeletingExistingRows() {
        String key = unique();
        long id = types.createDictType(typeForm(key));
        assertAll(
                () ->
                        error(
                                DICT_TYPE_NOT_EXISTS.getCode(),
                                () -> types.deleteDictTypeList(List.of(id, Long.MAX_VALUE))),
                () ->
                        assertNotNull(
                                types.getDictType(id),
                                "Mixed missing type batch deleted an existing row"));
    }

    @Test
    void missingDataInBatchRejectsWithoutDeletingExistingRows() {
        String key = unique();
        types.createDictType(typeForm(key));
        long id = values.createDictData(dataForm(key, "0", 0));
        assertAll(
                () ->
                        error(
                                DICT_DATA_NOT_EXISTS.getCode(),
                                () -> values.deleteDictDataList(List.of(id, Long.MAX_VALUE))),
                () ->
                        assertNotNull(
                                values.getDictData(id),
                                "Mixed missing data batch deleted an existing row"));
    }

    @Test
    void typeBatchRollsBackWhenALaterDatabaseUpdateFails() {
        String key = unique();
        long a = types.createDictType(typeForm(key + "A"));
        long b = types.createDictType(typeForm(key + "B"));
        var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute(
                "CREATE TRIGGER jss_dict_batch_abort BEFORE UPDATE ON system_dict_type FOR EACH ROW BEGIN IF OLD.id = "
                        + b
                        + " AND NEW.deleted = 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'owned dict rollback probe'; END IF; END");
        try {
            assertThrows(RuntimeException.class, () -> types.deleteDictTypeList(List.of(a, b)));
            assertNotNull(types.getDictType(a), "First type deletion escaped a later SQL failure");
            assertNotNull(types.getDictType(b));
        } finally {
            jdbc.execute("DROP TRIGGER jss_dict_batch_abort");
        }
    }

    @Test
    void successfulBatchesJoinTheCallersRollbackAndAcceptDuplicateIds() {
        String key = unique();
        long type = types.createDictType(typeForm(key));
        long data = values.createDictData(dataForm(key, "0", 0));
        var transaction =
                new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                status -> {
                                    values.deleteDictDataList(List.of(data, data));
                                    types.deleteDictTypeList(List.of(type, type));
                                    assertNull(types.getDictType(type));
                                    throw new IllegalStateException("owned rollback");
                                }));
        assertNotNull(types.getDictType(type));
        assertNotNull(values.getDictData(data));
        values.deleteDictDataList(List.of(data, data));
        types.deleteDictTypeList(List.of(type, type));
        assertNull(types.getDictType(type));
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
    @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass = true)
    @Import({DictTypeServiceImpl.class, DictDataServiceImpl.class})
    static class Config {
        @Bean
        org.springframework.jdbc.datasource.DataSourceTransactionManager transactionManager(
                DataSource source) {
            return new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
        }

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
            var config = new MybatisConfiguration();
            config.setEnvironment(
                    new Environment(
                            "jss-dict-services", new SpringManagedTransactionFactory(), source));
            config.setMapUnderscoreToCamelCase(true);
            config.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
            GlobalConfigUtils.setGlobalConfig(
                    config,
                    new GlobalConfig()
                            .setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO))
                            .setMetaObjectHandler(new DefaultDBFieldHandler())
                            .setSqlInjector(new YudaoSqlInjector()));
            var plugins = new MybatisPlusInterceptor();
            plugins.addInnerInterceptor(
                    new TenantLineInnerInterceptor(
                            new TenantDatabaseInterceptor(new TenantProperties())));
            plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            config.addInterceptor(plugins);
            config.addMapper(DictTypeMapper.class);
            config.addMapper(DictDataMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(config);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        DictTypeMapper loginLogMapper(SqlSessionTemplate sql) {
            return sql.getMapper(DictTypeMapper.class);
        }

        @Bean
        DictDataMapper operateLogMapper(SqlSessionTemplate sql) {
            return sql.getMapper(DictDataMapper.class);
        }
    }
}
