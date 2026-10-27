package cn.iocoder.yudao.module.trade.integration;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.trade.controller.admin.delivery.vo.expresstemplate.*;
import cn.iocoder.yudao.module.trade.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressTemplateService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressTemplateServiceImpl;

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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import javax.sql.DataSource;

/** Production template service, mappers, tenant SQL and transactions. No HTTP or Redis. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_freight_verify_[0-9a-f]{12}.*")
class JssFreightTemplateMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static DeliveryExpressTemplateService service;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager transactions;
    private static final List<String> TABLES =
            List.of(
                    "trade_delivery_express_template",
                    "trade_delivery_express_template_charge",
                    "trade_delivery_express_template_free");

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        transactions = context.getBean(DataSourceTransactionManager.class);
        var target = new DeliveryExpressTemplateServiceImpl();
        ReflectionTestUtils.setField(
                target,
                "expressTemplateMapper",
                context.getBean(DeliveryExpressTemplateMapper.class));
        ReflectionTestUtils.setField(
                target,
                "expressTemplateChargeMapper",
                context.getBean(DeliveryExpressTemplateChargeMapper.class));
        ReflectionTestUtils.setField(
                target,
                "expressTemplateFreeMapper",
                context.getBean(DeliveryExpressTemplateFreeMapper.class));
        var advice = new TransactionInterceptor();
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        advice.setTransactionManager(transactions);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(advice);
        service = (DeliveryExpressTemplateService) proxy.getProxy();
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_freight_verify_[0-9a-f]{12}"));
        }
    }

    @BeforeEach
    void clean() {
        TenantContextHolder.setTenantId(1L);
        for (String table : TABLES) {
            jdbc.execute("DELETE FROM " + table);
            jdbc.execute("ALTER TABLE " + table + " AUTO_INCREMENT=9007199254740993");
        }
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
    @Import(SpringUtil.class)
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
                    new Environment("jss-freight", new SpringManagedTransactionFactory(), source));
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
            plugins.addInnerInterceptor(new PaginationInnerInterceptor());
            config.addInterceptor(plugins);
            config.addMapper(DeliveryExpressTemplateMapper.class);
            config.addMapper(DeliveryExpressTemplateChargeMapper.class);
            config.addMapper(DeliveryExpressTemplateFreeMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(config);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        DeliveryExpressTemplateMapper templates(SqlSessionTemplate sql) {
            return sql.getMapper(DeliveryExpressTemplateMapper.class);
        }

        @Bean
        DeliveryExpressTemplateChargeMapper charges(SqlSessionTemplate sql) {
            return sql.getMapper(DeliveryExpressTemplateChargeMapper.class);
        }

        @Bean
        DeliveryExpressTemplateFreeMapper frees(SqlSessionTemplate sql) {
            return sql.getMapper(DeliveryExpressTemplateFreeMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    private DeliveryExpressTemplateChargeBaseVO charge(int area, int cents) {
        return new DeliveryExpressTemplateChargeBaseVO()
                .setAreaIds(List.of(area))
                .setStartCount(1D)
                .setStartPrice(cents)
                .setExtraCount(1D)
                .setExtraPrice(29);
    }

    private DeliveryExpressTemplateFreeBaseVO free(int area) {
        return new DeliveryExpressTemplateFreeBaseVO()
                .setAreaIds(List.of(area))
                .setFreeCount(3)
                .setFreePrice(300);
    }

    private DeliveryExpressTemplateCreateReqVO request(String name) {
        var req = new DeliveryExpressTemplateCreateReqVO();
        req.setName(name);
        req.setChargeMode(1);
        req.setSort(0);
        req.setCharges(List.of(charge(110101, 29)));
        req.setFrees(List.of(free(110101)));
        return req;
    }

    private DeliveryExpressTemplateUpdateReqVO update(Long id) {
        var detail = service.getDeliveryExpressTemplate(id);
        var req = new DeliveryExpressTemplateUpdateReqVO();
        req.setId(id);
        req.setName(detail.getName());
        req.setChargeMode(detail.getChargeMode());
        req.setSort(detail.getSort());
        req.setCharges(detail.getCharges());
        req.setFrees(detail.getFrees());
        return req;
    }

    private List<List<Map<String, Object>>> snapshot() {
        return TABLES.stream()
                .map(table -> jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"))
                .toList();
    }

    private void business(int code, Runnable action) {
        assertEquals(code, assertThrows(ServiceException.class, action::run).getCode());
    }

    @Test
    void createsAndReadsActualLongIdsAndRuleLists() {
        var id = service.createDeliveryExpressTemplate(request("Long template"));
        assertTrue(id > 9007199254740991L);
        var detail = service.getDeliveryExpressTemplate(id);
        assertEquals(29, detail.getCharges().getFirst().getStartPrice());
        assertTrue(detail.getCharges().getFirst().getId() > 9007199254740991L);
        assertEquals(List.of(110101), detail.getCharges().getFirst().getAreaIds());
        assertEquals(3, detail.getFrees().getFirst().getFreeCount());
        assertEquals(1, service.getDeliveryExpressTemplateList(List.of(id)).size());
        assertNull(service.getDeliveryExpressTemplate(99L));
    }

    @Test
    void updatesRetainedAndNewChargesAndClearsAllFreeRows() {
        var id = service.createDeliveryExpressTemplate(request("Update template"));
        var req = update(id);
        var retained = req.getCharges().getFirst();
        retained.setStartCount(0.29D).setStartPrice(129);
        req.setCharges(List.of(retained, charge(110102, 229)));
        req.setChargeMode(2);
        req.setFrees(List.of());
        service.updateDeliveryExpressTemplate(req);
        var result = service.getDeliveryExpressTemplate(id);
        assertEquals(2, result.getCharges().size());
        assertTrue(result.getFrees().isEmpty());
        assertTrue(
                result.getCharges().stream()
                        .anyMatch(
                                row ->
                                        Objects.equals(row.getId(), retained.getId())
                                                && row.getStartPrice() == 129
                                                && row.getStartCount() == 0.29D));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM trade_delivery_express_template_charge WHERE template_id=? AND charge_mode=2 AND deleted=0",
                        Integer.class,
                        id));
        req = update(id);
        req.setCharges(List.of(req.getCharges().getLast()));
        req.setFrees(null);
        service.updateDeliveryExpressTemplate(req);
        assertEquals(1, service.getDeliveryExpressTemplate(id).getCharges().size());
    }

    @Test
    void tenantBoundariesAndDuplicateNamesUseProductionQueries() {
        var first = service.createDeliveryExpressTemplate(request("Same name"));
        var initial = snapshot();
        business(
                EXPRESS_TEMPLATE_NAME_DUPLICATE.getCode(),
                () -> service.createDeliveryExpressTemplate(request("Same name")));
        assertEquals(initial, snapshot());
        TenantContextHolder.setTenantId(2L);
        assertNull(service.getDeliveryExpressTemplate(first));
        assertTrue(service.getDeliveryExpressTemplateList(List.of(first)).isEmpty());
        assertTrue(service.getExpressTemplateMapByIdsAndArea(List.of(first), 110101).isEmpty());
        business(
                EXPRESS_TEMPLATE_NOT_EXISTS.getCode(),
                () -> service.deleteDeliveryExpressTemplate(first));
        var second = service.createDeliveryExpressTemplate(request("Same name"));
        assertNotEquals(first, second);
        TenantContextHolder.setTenantId(1L);
        assertNull(service.getDeliveryExpressTemplate(second));
        assertEquals(1, service.getDeliveryExpressTemplateList().size());
    }

    @Test
    void pageFiltersRetainDatesModeSortAndSecondPage() {
        for (int index = 0; index < 3; index++) {
            var req = request("Page " + index);
            req.setSort(3 - index);
            var id = service.createDeliveryExpressTemplate(req);
            jdbc.update(
                    "UPDATE trade_delivery_express_template SET create_time=? WHERE id=?",
                    index == 2 ? "2026-10-03 00:00:00" : "2026-10-01 00:00:00",
                    id);
        }
        var query = new DeliveryExpressTemplatePageReqVO();
        query.setName("Page");
        query.setChargeMode(1);
        query.setPageNo(2);
        query.setPageSize(1);
        query.setCreateTime(
                new LocalDateTime[] {
                    LocalDateTime.parse("2026-10-01T00:00:00"),
                    LocalDateTime.parse("2026-10-02T23:59:59")
                });
        var page = service.getDeliveryExpressTemplatePage(query);
        assertEquals(2, page.getTotal());
        assertEquals("Page 0", page.getList().getFirst().getName());
    }

    @Test
    void failedFinalChildInsertRollsBackCreateAndUpdate() {
        jdbc.execute(
                "CREATE TRIGGER freight_fault BEFORE INSERT ON trade_delivery_express_template_free FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='freight test rollback'");
        try {
            assertThrows(
                    Exception.class,
                    () -> service.createDeliveryExpressTemplate(request("Fail create")));
            for (String table : TABLES)
                assertEquals(
                        0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        } finally {
            jdbc.execute("DROP TRIGGER freight_fault");
        }
        var id = service.createDeliveryExpressTemplate(request("Before"));
        var before = snapshot();
        var req = update(id);
        req.setName("After");
        req.getCharges().getFirst().setStartPrice(999);
        jdbc.execute(
                "CREATE TRIGGER freight_fault BEFORE INSERT ON trade_delivery_express_template_free FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='freight test rollback'");
        try {
            assertThrows(Exception.class, () -> service.updateDeliveryExpressTemplate(req));
            assertEquals(before, snapshot());
        } finally {
            jdbc.execute("DROP TRIGGER freight_fault");
        }
    }

    @Test
    void outerTransactionsRollBackAndDeleteCascadesLogically() {
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(
                status -> {
                    service.createDeliveryExpressTemplate(request("Rollback create"));
                    status.setRollbackOnly();
                });
        for (String table : TABLES)
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        var id = service.createDeliveryExpressTemplate(request("Delete"));
        var before = snapshot();
        transaction.executeWithoutResult(
                status -> {
                    service.deleteDeliveryExpressTemplate(id);
                    status.setRollbackOnly();
                });
        assertEquals(before, snapshot());
        service.deleteDeliveryExpressTemplate(id);
        assertNull(service.getDeliveryExpressTemplate(id));
        for (String table : TABLES) {
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM " + table + " WHERE deleted=0", Integer.class));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM " + table + " WHERE deleted=1", Integer.class));
        }
    }

    @Test
    void matchesExactRegionsWithoutInventingParentInheritance() {
        var id = service.createDeliveryExpressTemplate(request("Regions"));
        var req = update(id);
        req.setCharges(List.of(req.getCharges().getFirst(), charge(110102, 129)));
        req.setFrees(List.of(free(110103)));
        service.updateDeliveryExpressTemplate(req);
        assertEquals(
                29,
                service.getExpressTemplateMapByIdsAndArea(List.of(id), 110101)
                        .get(id)
                        .getCharge()
                        .getStartPrice());
        assertEquals(
                129,
                service.getExpressTemplateMapByIdsAndArea(List.of(id), 110102)
                        .get(id)
                        .getCharge()
                        .getStartPrice());
        var onlyFree = service.getExpressTemplateMapByIdsAndArea(List.of(id), 110103).get(id);
        assertNull(onlyFree.getCharge());
        assertNotNull(onlyFree.getFree());
        assertTrue(service.getExpressTemplateMapByIdsAndArea(List.of(id), 110100).isEmpty());
    }

    @Test
    void foreignChildIdsCannotReassignAnotherTemplatesRules() {
        var first = service.createDeliveryExpressTemplate(request("First"));
        var second = service.createDeliveryExpressTemplate(request("Second"));
        var before = snapshot();
        var req = update(first);
        req.setCharges(service.getDeliveryExpressTemplate(second).getCharges());
        assertThrows(Exception.class, () -> service.updateDeliveryExpressTemplate(req));
        assertEquals(before, snapshot());
    }

    @Test
    void concurrentFullReplacementsReadCurrentRulesAfterAnOldSnapshot() throws Exception {
        concurrentUpdates(false);
    }

    @Test
    void concurrentEditsRetainTheExistingChargeIdAndOneCompleteFinalRuleSet() throws Exception {
        concurrentUpdates(true);
    }

    private void concurrentUpdates(boolean retainChargeId) throws Exception {
        var id = service.createDeliveryExpressTemplate(request("Concurrent initial"));
        var originalChargeId =
                service.getDeliveryExpressTemplate(id).getCharges().getFirst().getId();
        var barrier = new CyclicBarrier(6);
        var pool = Executors.newFixedThreadPool(6);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int index = 1; index <= 6; index++) {
                int marker = index;
                futures.add(
                        pool.submit(
                                () -> {
                                    TenantContextHolder.setTenantId(1L);
                                    try {
                                        new TransactionTemplate(transactions)
                                                .executeWithoutResult(
                                                        status -> {
                                                            // Establish RR and the MyBatis session
                                                            // cache before any contender writes.
                                                            assertEquals(
                                                                    1,
                                                                    service.getDeliveryExpressTemplate(
                                                                                    id)
                                                                            .getCharges()
                                                                            .size());
                                                            try {
                                                                barrier.await(15, TimeUnit.SECONDS);
                                                            } catch (Exception error) {
                                                                throw new RuntimeException(error);
                                                            }
                                                            var req =
                                                                    new DeliveryExpressTemplateUpdateReqVO();
                                                            req.setId(id);
                                                            req.setName("Concurrent " + marker);
                                                            req.setSort(marker);
                                                            req.setChargeMode(marker % 3 + 1);
                                                            req.setCharges(
                                                                    List.of(
                                                                            charge(
                                                                                            110100
                                                                                                    + marker,
                                                                                            100
                                                                                                    + marker)
                                                                                    .setId(
                                                                                            retainChargeId
                                                                                                    ? originalChargeId
                                                                                                    : null)));
                                                            req.setFrees(
                                                                    List.of(
                                                                            new DeliveryExpressTemplateFreeBaseVO()
                                                                                    .setAreaIds(
                                                                                            List.of(
                                                                                                    210100
                                                                                                            + marker))
                                                                                    .setFreeCount(
                                                                                            marker)
                                                                                    .setFreePrice(
                                                                                            200
                                                                                                    + marker)));
                                                            service.updateDeliveryExpressTemplate(
                                                                    req);
                                                        });
                                    } finally {
                                        TenantContextHolder.clear();
                                    }
                                }));
            }
            for (var future : futures) assertDoesNotThrow(() -> future.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        }
        var result = service.getDeliveryExpressTemplate(id);
        int marker = result.getSort();
        assertTrue(marker >= 1 && marker <= 6);
        assertEquals("Concurrent " + marker, result.getName());
        assertEquals(marker % 3 + 1, result.getChargeMode());
        assertEquals(1, result.getCharges().size());
        if (retainChargeId) assertEquals(originalChargeId, result.getCharges().getFirst().getId());
        assertEquals(1, result.getFrees().size());
        assertEquals(List.of(110100 + marker), result.getCharges().getFirst().getAreaIds());
        assertEquals(100 + marker, result.getCharges().getFirst().getStartPrice());
        assertEquals(List.of(210100 + marker), result.getFrees().getFirst().getAreaIds());
        assertEquals(200 + marker, result.getFrees().getFirst().getFreePrice());
        assertEquals(
                marker % 3 + 1,
                jdbc.queryForObject(
                        "SELECT charge_mode FROM trade_delivery_express_template_charge WHERE template_id=? AND deleted=0",
                        Integer.class,
                        id));
    }

    @Test
    void anOldSnapshotCannotRestoreRulesAfterAnotherTransactionDeletesTheTemplate()
            throws Exception {
        var id = service.createDeliveryExpressTemplate(request("Delete race"));
        var req = update(id);
        req.setCharges(List.of(charge(110102, 229)));
        var pool = Executors.newSingleThreadExecutor();
        try {
            business(
                    EXPRESS_TEMPLATE_NOT_EXISTS.getCode(),
                    () ->
                            new TransactionTemplate(transactions)
                                    .executeWithoutResult(
                                            status -> {
                                                assertNotNull(
                                                        service.getDeliveryExpressTemplate(id));
                                                var deleted =
                                                        pool.submit(
                                                                () -> {
                                                                    TenantContextHolder.setTenantId(
                                                                            1L);
                                                                    try {
                                                                        service
                                                                                .deleteDeliveryExpressTemplate(
                                                                                        id);
                                                                    } finally {
                                                                        TenantContextHolder.clear();
                                                                    }
                                                                });
                                                assertDoesNotThrow(
                                                        () -> deleted.get(15, TimeUnit.SECONDS));
                                                service.updateDeliveryExpressTemplate(req);
                                            }));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        }
        assertNull(service.getDeliveryExpressTemplate(id));
        for (String table : TABLES)
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM " + table + " WHERE deleted=0", Integer.class));
    }
}
