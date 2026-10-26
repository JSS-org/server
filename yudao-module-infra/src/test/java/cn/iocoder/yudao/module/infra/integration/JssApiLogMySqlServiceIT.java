package cn.iocoder.yudao.module.infra.integration;

import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogRespVO;
import cn.iocoder.yudao.module.infra.dal.mysql.logger.ApiAccessLogMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.logger.ApiErrorLogMapper;
import cn.iocoder.yudao.module.infra.service.logger.ApiAccessLogService;
import cn.iocoder.yudao.module.infra.service.logger.ApiAccessLogServiceImpl;
import cn.iocoder.yudao.module.infra.service.logger.ApiErrorLogService;
import cn.iocoder.yudao.module.infra.service.logger.ApiErrorLogServiceImpl;

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
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import javax.sql.DataSource;

/** Actual MySQL, service, mapper, tenant/pagination SQL and transactions; no HTTP or Redis. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_apilog_service_verify_[0-9a-f]{12}.*")
class JssApiLogMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static ApiErrorLogService service;
    private static ApiErrorLogMapper mapper;
    private static ApiAccessLogService access;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(ApiErrorLogService.class);
        mapper = context.getBean(ApiErrorLogMapper.class);
        access = context.getBean(ApiAccessLogService.class);
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_apilog_service_verify_[0-9a-f]{12}"));
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
    @Import({ApiErrorLogServiceImpl.class, ApiAccessLogServiceImpl.class})
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
                            "jss-apilog-services", new SpringManagedTransactionFactory(), source));
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
            configuration.addMapper(ApiErrorLogMapper.class);
            configuration.addMapper(ApiAccessLogMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        ApiErrorLogMapper configMapper(SqlSessionTemplate sql) {
            return sql.getMapper(ApiErrorLogMapper.class);
        }

        @Bean
        ApiAccessLogMapper accessMapper(SqlSessionTemplate sql) {
            return sql.getMapper(ApiAccessLogMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 27, 1, 2, 3);

    private String unique() {
        return "JSSApiLog" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private ApiAccessLogCreateReqDTO accessForm(String app) {
        return new ApiAccessLogCreateReqDTO()
                .setTraceId(app)
                .setUserId(0L)
                .setUserType(2)
                .setApplicationName(app)
                .setRequestMethod("GET")
                .setRequestUrl("/synthetic/zero")
                .setRequestParams("{\"synthetic\":true}")
                .setResponseBody("{\"code\":0}")
                .setUserIp("127.0.0.1")
                .setUserAgent("JSS isolated test")
                .setOperateModule("test")
                .setOperateName("read")
                .setOperateType(1)
                .setBeginTime(TIME)
                .setEndTime(TIME)
                .setDuration(0)
                .setResultCode(0)
                .setResultMsg("");
    }

    private ApiErrorLogCreateReqDTO errorForm(String app) {
        return new ApiErrorLogCreateReqDTO()
                .setTraceId(app)
                .setUserId(0L)
                .setUserType(2)
                .setApplicationName(app)
                .setRequestMethod("POST")
                .setRequestUrl("/synthetic/error")
                .setRequestParams("{\"synthetic\":true}")
                .setUserIp("127.0.0.1")
                .setUserAgent("JSS isolated test")
                .setExceptionTime(TIME)
                .setExceptionName("SyntheticException")
                .setExceptionMessage("synthetic message")
                .setExceptionRootCauseMessage("synthetic root")
                .setExceptionStackTrace("synthetic stack")
                .setExceptionClassName("example.Test")
                .setExceptionFileName("Test.java")
                .setExceptionMethodName("run")
                .setExceptionLineNumber(0);
    }

    private long error() {
        String app = unique();
        service.createApiErrorLog(errorForm(app));
        var rows = service.getApiErrorLogPage(new ApiErrorLogPageReqVO().setApplicationName(app));
        assertEquals(1, rows.getTotal());
        return rows.getList().get(0).getId();
    }

    private void errorCode(
            cn.iocoder.yudao.framework.common.exception.ErrorCode code,
            org.junit.jupiter.api.function.Executable action) {
        assertEquals(code.getCode(), assertThrows(ServiceException.class, action).getCode());
    }

    @Test
    void accessFiltersUseBeginTimeAndKeepZeroValuesAndTenantIsolation() {
        String app = unique();
        access.createApiAccessLog(accessForm(app));
        access.createApiAccessLog(accessForm(app).setResultCode(500).setDuration(10));
        access.createApiAccessLog(accessForm(app).setUserId(1L));
        var query =
                new ApiAccessLogPageReqVO()
                        .setUserId(0L)
                        .setUserType(2)
                        .setApplicationName(app)
                        .setRequestUrl("zero")
                        .setBeginTime(
                                new LocalDateTime[] {TIME.minusSeconds(1), TIME.plusSeconds(1)})
                        .setDuration(0)
                        .setResultCode(0);
        var page = access.getApiAccessLogPage(query);
        assertEquals(1, page.getTotal());
        var row = page.getList().get(0);
        assertEquals(0L, row.getUserId());
        assertEquals("{\"code\":0}", row.getResponseBody());
        assertNotNull(access.getApiAccessLog(row.getId()));
        assertEquals(0, access.getApiAccessLogPage(query.setDuration(1)).getTotal());
        var all = new ApiAccessLogPageReqVO().setApplicationName(app);
        all.setPageSize(1);
        assertEquals(3, access.getApiAccessLogPage(all).getTotal());
        assertEquals(1, access.getApiAccessLogPage(all).getList().size());
        TenantContextHolder.setTenantId(2L);
        assertNull(access.getApiAccessLog(row.getId()));
        assertEquals(0, access.getApiAccessLogPage(all).getTotal());
    }

    @Test
    void errorFiltersUseExceptionTimeAndForeignRowsCannotBeProcessed() {
        long id = error();
        var row = service.getApiErrorLog(id);
        var query =
                new ApiErrorLogPageReqVO()
                        .setUserId(0L)
                        .setUserType(2)
                        .setApplicationName(row.getApplicationName())
                        .setRequestUrl("error")
                        .setExceptionTime(
                                new LocalDateTime[] {TIME.minusSeconds(1), TIME.plusSeconds(1)})
                        .setProcessStatus(0);
        assertEquals(1, service.getApiErrorLogPage(query).getTotal());
        assertEquals(0, service.getApiErrorLogPage(query.setProcessStatus(1)).getTotal());
        TenantContextHolder.setTenantId(2L);
        assertNull(service.getApiErrorLog(id));
        assertEquals(0, service.getApiErrorLogPage(query.setProcessStatus(0)).getTotal());
        errorCode(API_ERROR_LOG_NOT_FOUND, () -> service.updateApiErrorLogProcess(id, 1, 11L));
        TenantContextHolder.setTenantId(1L);
        assertEquals(0, service.getApiErrorLog(id).getProcessStatus());
    }

    @Test
    void terminalProcessingKeepsLongActorAndCannotBeRewritten() {
        long id = error();
        long actor = 9007199254740993L;
        service.updateApiErrorLogProcess(id, 1, actor);
        var stored = service.getApiErrorLog(id);
        assertEquals(actor, stored.getProcessUserId());
        assertEquals(1, stored.getProcessStatus());
        assertNotNull(stored.getProcessTime());
        assertEquals(
                Long.valueOf(actor),
                BeanUtils.toBean(stored, ApiErrorLogRespVO.class).getProcessUserId());
        errorCode(API_ERROR_LOG_PROCESSED, () -> service.updateApiErrorLogProcess(id, 2, 12L));
        assertEquals(actor, service.getApiErrorLog(id).getProcessUserId());
    }

    @Test
    void onlyDoneOrIgnoreAreAllowedAndInvalidRequestsDoNotWriteMetadata() {
        long id = error();
        for (Integer status : Arrays.asList(0, -1, 3, Integer.MAX_VALUE, null))
            assertThrows(
                    ServiceException.class,
                    () -> service.updateApiErrorLogProcess(id, status, 12L));
        var stored = service.getApiErrorLog(id);
        assertEquals(0, stored.getProcessStatus());
        assertEquals(0L, stored.getProcessUserId());
        assertNull(stored.getProcessTime());
    }

    @Test
    void competingProcessorsHaveExactlyOneWinner() throws Exception {
        long id = error();
        var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute(
                "CREATE TRIGGER jss_process_delay BEFORE UPDATE ON infra_api_error_log FOR EACH ROW BEGIN IF OLD.id="
                        + id
                        + " THEN DO SLEEP(0.25); END IF; END");
        var executor = Executors.newFixedThreadPool(6);
        var barrier = new CyclicBarrier(6);
        var futures = new ArrayList<Future<Long>>();
        try {
            for (int index = 0; index < 6; index++) {
                final int candidate = index;
                futures.add(
                        executor.submit(
                                () -> {
                                    TenantContextHolder.setTenantId(1L);
                                    try {
                                        barrier.await(10, TimeUnit.SECONDS);
                                        service.updateApiErrorLogProcess(
                                                id, candidate % 2 + 1, 100L + candidate);
                                        return 100L + candidate;
                                    } catch (ServiceException exception) {
                                        assertEquals(
                                                API_ERROR_LOG_PROCESSED.getCode(),
                                                exception.getCode());
                                        return null;
                                    } finally {
                                        TenantContextHolder.clear();
                                    }
                                }));
            }
            var winners = new ArrayList<Long>();
            for (var future : futures) {
                Long winner = future.get(20, TimeUnit.SECONDS);
                if (winner != null) winners.add(winner);
            }
            assertEquals(1, winners.size());
            assertEquals(winners.get(0), service.getApiErrorLog(id).getProcessUserId());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            jdbc.execute("DROP TRIGGER jss_process_delay");
        }
    }

    @Test
    void processingParticipatesInOuterRollback() {
        long id = error();
        new TransactionTemplate(context.getBean(DataSourceTransactionManager.class))
                .executeWithoutResult(
                        tx -> {
                            service.updateApiErrorLogProcess(id, 2, 12L);
                            assertEquals(2, service.getApiErrorLog(id).getProcessStatus());
                            tx.setRollbackOnly();
                        });
        assertEquals(0, service.getApiErrorLog(id).getProcessStatus());
        assertEquals(0L, service.getApiErrorLog(id).getProcessUserId());
        assertNull(service.getApiErrorLog(id).getProcessTime());
    }
}
