package cn.iocoder.yudao.module.system.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.logger.LoginLogDO;
import cn.iocoder.yudao.module.system.dal.dataobject.logger.OperateLogDO;
import cn.iocoder.yudao.module.system.dal.mysql.logger.LoginLogMapper;
import cn.iocoder.yudao.module.system.dal.mysql.logger.OperateLogMapper;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.service.logger.LoginLogServiceImpl;
import cn.iocoder.yudao.module.system.service.logger.OperateLogService;
import cn.iocoder.yudao.module.system.service.logger.OperateLogServiceImpl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.time.LocalDateTime;
import java.util.*;

import javax.sql.DataSource;

/** Production audit services and tenant/pagination SQL on disposable MySQL; no HTTP or doubles. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_audit_service_verify_[0-9a-f]{12}.*")
class JssAuditMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static LoginLogService logins;
    private static OperateLogService operations;
    private static LoginLogMapper loginMapper;
    private static OperateLogMapper operateMapper;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        logins = context.getBean(LoginLogService.class);
        operations = context.getBean(OperateLogService.class);
        loginMapper = context.getBean(LoginLogMapper.class);
        operateMapper = context.getBean(OperateLogMapper.class);
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_audit_service_verify_[0-9a-f]{12}"));
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
    @Import({LoginLogServiceImpl.class, OperateLogServiceImpl.class})
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
            var config = new MybatisConfiguration();
            config.setEnvironment(
                    new Environment(
                            "jss-audit-services", new SpringManagedTransactionFactory(), source));
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
            config.addMapper(LoginLogMapper.class);
            config.addMapper(OperateLogMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(config);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        LoginLogMapper loginLogMapper(SqlSessionTemplate sql) {
            return sql.getMapper(LoginLogMapper.class);
        }

        @Bean
        OperateLogMapper operateLogMapper(SqlSessionTemplate sql) {
            return sql.getMapper(OperateLogMapper.class);
        }
    }

    private String unique() {
        return "JSSAudit" + UUID.randomUUID().toString().substring(0, 8);
    }

    private LoginLogPageReqVO loginQuery(String name) {
        var req = new LoginLogPageReqVO().setUsername(name);
        req.setPageNo(1);
        req.setPageSize(20);
        return req;
    }

    private OperateLogPageReqVO operateQuery(String type) {
        var req = new OperateLogPageReqVO().setType(type);
        req.setPageNo(1);
        req.setPageSize(20);
        return req;
    }

    private LoginLogDO login(String name, int result, String ip) {
        logins.createLoginLog(
                new LoginLogCreateReqDTO()
                        .setLogType(100)
                        .setTraceId(unique())
                        .setUserId(9007199254740993L)
                        .setUserType(2)
                        .setUsername(name)
                        .setResult(result)
                        .setUserIp(ip)
                        .setUserAgent("JSS audit test"));
        return logins.getLoginLogPage(loginQuery(name)).getList().getFirst();
    }

    private OperateLogDO operation(String type, long user, long biz, String action) {
        operations.createOperateLog(
                new OperateLogCreateReqDTO()
                        .setTraceId(unique())
                        .setUserId(user)
                        .setUserType(2)
                        .setType(type)
                        .setSubType("UPDATE")
                        .setBizId(biz)
                        .setAction(action)
                        .setExtra("{\"source\":\"owned test\"}")
                        .setRequestMethod("PUT")
                        .setRequestUrl("/admin-api/system/user/update")
                        .setUserIp("127.0.0.1")
                        .setUserAgent("JSS audit test"));
        return operations.getOperateLogPage(operateQuery(type)).getList().getFirst();
    }

    @Test
    void loginPaginationCombinesAccountIpBooleanResultAndDateRange() {
        String name = unique();
        var old = login(name + "A", 0, "10.0.0.1");
        var failure = login(name + "B", 10, "10.0.0.2");
        var fresh = login(name + "C", 0, "10.0.0.3");
        var now = LocalDateTime.now();
        var oldUpdate = new LoginLogDO().setId(old.getId());
        oldUpdate.setCreateTime(now.minusDays(2));
        loginMapper.updateById(oldUpdate);
        var query =
                loginQuery(name)
                        .setUserIp("10.0.")
                        .setStatus(true)
                        .setCreateTime(new LocalDateTime[] {now.minusHours(1), now.plusHours(1)});
        var page = logins.getLoginLogPage(query);
        assertEquals(1, page.getTotal());
        assertEquals(fresh.getId(), page.getList().getFirst().getId());
        query.setStatus(false);
        assertEquals(failure.getId(), logins.getLoginLogPage(query).getList().getFirst().getId());
        var all = loginQuery(name);
        all.setPageSize(2);
        assertEquals(3, logins.getLoginLogPage(all).getTotal());
        assertEquals(2, logins.getLoginLogPage(all).getList().size());
        all.setPageNo(2);
        assertEquals(old.getId(), logins.getLoginLogPage(all).getList().getFirst().getId());
    }

    @Test
    void operateFiltersKeepExactLargeActorAndBusinessIdsAndBoundDates() {
        String type = unique();
        long actor = 9007199254740993L;
        long biz = 9007199254740995L;
        var old = operation(type, actor, biz, "changed nickname");
        var oldUpdate = new OperateLogDO().setId(old.getId());
        oldUpdate.setCreateTime(LocalDateTime.now().minusDays(2));
        operateMapper.updateById(oldUpdate);
        operation(type, 2, biz, "changed nickname");
        operation(type, actor, 3, "changed nickname");
        operation(type, actor, biz, "changed status");
        var match = operation(type, actor, biz, "changed nickname");
        var query =
                operateQuery(type)
                        .setUserId(actor)
                        .setBizId(biz)
                        .setSubType("UPD")
                        .setAction("nickname")
                        .setCreateTime(
                                new LocalDateTime[] {
                                    LocalDateTime.now().minusHours(1),
                                    LocalDateTime.now().plusHours(1)
                                });
        var page = operations.getOperateLogPage(query);
        assertEquals(1, page.getTotal());
        assertEquals(match.getId(), page.getList().getFirst().getId());
        assertEquals(actor, page.getList().getFirst().getUserId());
        assertEquals(biz, page.getList().getFirst().getBizId());
    }

    @Test
    void loginAndOperateDetailsAndPagesStayInsideTheCurrentTenant() {
        String name = unique();
        var ownLogin = login(name, 0, "127.0.0.1");
        var ownOp = operation(name, 1, 2, "own");
        TenantContextHolder.setTenantId(2L);
        var foreignLogin = login(name, 0, "127.0.0.1");
        var foreignOp = operation(name, 1, 2, "foreign");
        assertNull(logins.getLoginLog(ownLogin.getId()));
        assertNull(operations.getOperateLog(ownOp.getId()));
        TenantContextHolder.setTenantId(1L);
        assertNull(logins.getLoginLog(foreignLogin.getId()));
        assertNull(operations.getOperateLog(foreignOp.getId()));
        assertEquals(
                List.of(ownLogin.getId()),
                logins.getLoginLogPage(loginQuery(name)).getList().stream()
                        .map(LoginLogDO::getId)
                        .toList());
        assertEquals(
                List.of(ownOp.getId()),
                operations.getOperateLogPage(operateQuery(name)).getList().stream()
                        .map(OperateLogDO::getId)
                        .toList());
    }

    @Test
    void socialAndScheduledLogoutAcceptMissingOptionalUsernameAndUserAgent() {
        for (int type : List.of(101, 200)) {
            String trace = unique();
            logins.createLoginLog(
                    new LoginLogCreateReqDTO()
                            .setTraceId(trace)
                            .setLogType(type)
                            .setUserType(2)
                            .setResult(0)
                            .setUserIp("127.0.0.1"));
            var saved =
                    loginMapper.selectOne(
                            new LambdaQueryWrapper<LoginLogDO>().eq(LoginLogDO::getTraceId, trace));
            assertNotNull(saved);
            assertEquals("", saved.getUsername());
            assertEquals("", saved.getUserAgent());
        }
    }
}
