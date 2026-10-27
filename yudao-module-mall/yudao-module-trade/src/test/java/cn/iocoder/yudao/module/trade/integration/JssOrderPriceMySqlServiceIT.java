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
import cn.iocoder.yudao.module.pay.api.order.PayOrderApiImpl;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.dal.mysql.order.PayOrderMapper;
import cn.iocoder.yudao.module.pay.service.order.PayOrderService;
import cn.iocoder.yudao.module.pay.service.order.PayOrderServiceImpl;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderUpdatePriceReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateServiceImpl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
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

/** Actual order/item/payment price transactions. No providers, Redis, HTTP or log aspect. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_order_price_verify_[0-9a-f]{12}.*")
class JssOrderPriceMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static TradeOrderMapper orders;
    private static TradeOrderItemMapper items;
    private static PayOrderMapper payments;
    private static TradeOrderUpdateService service;
    private static PayOrderService payService;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager transactions;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        orders = context.getBean(TradeOrderMapper.class);
        items = context.getBean(TradeOrderItemMapper.class);
        payments = context.getBean(PayOrderMapper.class);
        transactions = context.getBean(DataSourceTransactionManager.class);
        var payTarget = new PayOrderServiceImpl();
        ReflectionTestUtils.setField(payTarget, "orderMapper", payments);
        payService = (PayOrderService) transactional(payTarget);
        var payApi = new PayOrderApiImpl();
        ReflectionTestUtils.setField(payApi, "payOrderService", payService);
        var target = new TradeOrderUpdateServiceImpl();
        ReflectionTestUtils.setField(target, "tradeOrderMapper", orders);
        ReflectionTestUtils.setField(target, "tradeOrderItemMapper", items);
        ReflectionTestUtils.setField(target, "payOrderApi", payApi);
        service = (TradeOrderUpdateService) transactional(target);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_order_price_verify_[0-9a-f]{12}"));
        }
    }

    private static Object transactional(Object target) {
        var advice = new TransactionInterceptor();
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        advice.setTransactionManager(transactions);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(advice);
        return proxy.getProxy();
    }

    @BeforeEach
    void clean() {
        TenantContextHolder.setTenantId(1L);
        for (String table : List.of("trade_order_item", "trade_order", "pay_order"))
            jdbc.execute("DELETE FROM " + table);
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
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(
                    new Environment(
                            "jss-order-price", new SpringManagedTransactionFactory(), source));
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
            configuration.addInterceptor(plugins);
            configuration.addMapper(TradeOrderMapper.class);
            configuration.addMapper(TradeOrderItemMapper.class);
            configuration.addMapper(PayOrderMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        TradeOrderMapper orderMapper(SqlSessionTemplate sql) {
            return sql.getMapper(TradeOrderMapper.class);
        }

        @Bean
        TradeOrderItemMapper itemMapper(SqlSessionTemplate sql) {
            return sql.getMapper(TradeOrderItemMapper.class);
        }

        @Bean
        PayOrderMapper payMapper(SqlSessionTemplate sql) {
            return sql.getMapper(PayOrderMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    private TradeOrderDO order() {
        var payment =
                new PayOrderDO()
                        .setAppId(1L)
                        .setUserId(10L)
                        .setUserType(1)
                        .setMerchantOrderId(UUID.randomUUID().toString())
                        .setSubject("Price fixture")
                        .setPrice(100)
                        .setStatus(0)
                        .setUserIp("127.0.0.1")
                        .setExpireTime(LocalDateTime.now().plusDays(1));
        payments.insert(payment);
        var order =
                new TradeOrderDO()
                        .setNo(UUID.randomUUID().toString())
                        .setUserId(10L)
                        .setStatus(0)
                        .setPayStatus(false)
                        .setPayOrderId(payment.getId())
                        .setPayPrice(100)
                        .setAdjustPrice(0)
                        .setTotalPrice(100)
                        .setProductCount(2)
                        .setDeliveryType(1);
        orders.insert(order);
        for (int index = 0; index < 2; index++)
            items.insert(
                    new TradeOrderItemDO()
                            .setUserId(10L)
                            .setOrderId(order.getId())
                            .setSpuId((long) index + 1)
                            .setSkuId((long) index + 11)
                            .setCount(1)
                            .setPrice(index == 0 ? 60 : 40)
                            .setPayPrice(index == 0 ? 60 : 40)
                            .setAdjustPrice(0));
        return order;
    }

    private void adjust(Long id, Integer delta) {
        service.updateOrderPrice(new TradeOrderUpdatePriceReqVO().setId(id).setAdjustPrice(delta));
    }

    private void assertPrices(TradeOrderDO original, int expected, int adjustment) {
        assertEquals(expected, orders.selectById(original.getId()).getPayPrice());
        assertEquals(adjustment, orders.selectById(original.getId()).getAdjustPrice());
        assertEquals(expected, payments.selectById(original.getPayOrderId()).getPrice());
        assertEquals(
                expected,
                jdbc.queryForObject(
                        "SELECT SUM(pay_price) FROM trade_order_item WHERE order_id=?",
                        Integer.class,
                        original.getId()));
        assertEquals(
                adjustment,
                jdbc.queryForObject(
                        "SELECT SUM(adjust_price) FROM trade_order_item WHERE order_id=?",
                        Integer.class,
                        original.getId()));
    }

    @Test
    void signedAdjustmentsKeepHeaderItemsAndPaymentEqual() {
        var first = order();
        adjust(first.getId(), 29);
        assertPrices(first, 129, 29);
        var second = order();
        adjust(second.getId(), -29);
        assertPrices(second, 71, -29);
    }

    @Test
    void aNegativeAdjustmentCannotBeChangedAgainOrResetToZero() {
        var order = order();
        adjust(order.getId(), -10);
        var error = assertThrows(ServiceException.class, () -> adjust(order.getId(), 10));
        assertEquals(ORDER_UPDATE_PRICE_FAIL_ALREADY.getCode(), error.getCode());
        assertPrices(order, 90, -10);
    }

    @Test
    void sixCompetingAdjustmentsFromOldSnapshotsOnlyAllowOne() throws Exception {
        var order = order();
        var ready = new CountDownLatch(6);
        var executor = Executors.newFixedThreadPool(6);
        var futures = new ArrayList<Future<Throwable>>();
        try {
            for (int index = 0; index < 6; index++)
                futures.add(
                        executor.submit(
                                () -> {
                                    TenantContextHolder.setTenantId(1L);
                                    try {
                                        new TransactionTemplate(transactions)
                                                .executeWithoutResult(
                                                        tx -> {
                                                            assertEquals(
                                                                    100,
                                                                    orders.selectById(order.getId())
                                                                            .getPayPrice());
                                                            payments.selectById(
                                                                    order.getPayOrderId());
                                                            ready.countDown();
                                                            try {
                                                                assertTrue(
                                                                        ready.await(
                                                                                20,
                                                                                TimeUnit.SECONDS));
                                                            } catch (InterruptedException ex) {
                                                                throw new RuntimeException(ex);
                                                            }
                                                            adjust(order.getId(), -10);
                                                        });
                                        return null;
                                    } catch (Throwable error) {
                                        return error;
                                    } finally {
                                        TenantContextHolder.clear();
                                    }
                                }));
            var errors = new ArrayList<Throwable>();
            for (var future : futures) {
                var error = future.get(60, TimeUnit.SECONDS);
                if (error != null) errors.add(error);
            }
            assertEquals(5, errors.size());
            for (var error : errors)
                assertEquals(
                        ORDER_UPDATE_PRICE_FAIL_ALREADY.getCode(),
                        assertInstanceOf(ServiceException.class, error).getCode());
            assertPrices(order, 90, -10);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void paidAndClosedTradeOrdersRejectAdjustments() {
        var paid = order();
        orders.updateById(new TradeOrderDO().setId(paid.getId()).setPayStatus(true).setStatus(10));
        assertThrows(ServiceException.class, () -> adjust(paid.getId(), 10));
        assertPrices(paid, 100, 0);
        var closed = order();
        orders.updateById(new TradeOrderDO().setId(closed.getId()).setStatus(40));
        assertThrows(ServiceException.class, () -> adjust(closed.getId(), 10));
        assertPrices(closed, 100, 0);
    }

    @Test
    void invisibleOrdersAndPaymentsDoNotPermitCrossTenantWrites() {
        var original = order();
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class, () -> adjust(original.getId(), 10));
        assertThrows(
                ServiceException.class,
                () -> payService.updatePayOrderPrice(original.getPayOrderId(), 90));
        TenantContextHolder.setTenantId(1L);
        assertPrices(original, 100, 0);
    }

    @Test
    void paymentStatusCommittedAfterAnOldSnapshotPreventsAnyPriceChange() throws Exception {
        var order = order();
        var executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            tx -> {
                                assertEquals(
                                        0, payments.selectById(order.getPayOrderId()).getStatus());
                                try {
                                    executor.submit(
                                                    () -> {
                                                        TenantContextHolder.setTenantId(1L);
                                                        try {
                                                            payments.updateById(
                                                                    new PayOrderDO()
                                                                            .setId(
                                                                                    order
                                                                                            .getPayOrderId())
                                                                            .setStatus(10));
                                                        } finally {
                                                            TenantContextHolder.clear();
                                                        }
                                                    })
                                            .get(20, TimeUnit.SECONDS);
                                } catch (Exception ex) {
                                    throw new RuntimeException(ex);
                                }
                                assertThrows(
                                        ServiceException.class,
                                        () ->
                                                payService.updatePayOrderPrice(
                                                        order.getPayOrderId(), 90));
                                assertThrows(
                                        ServiceException.class,
                                        () ->
                                                payService.updatePayOrderPrice(
                                                        order.getPayOrderId(), 100));
                                tx.setRollbackOnly();
                            });
        } finally {
            executor.shutdownNow();
        }
        assertEquals(10, payments.selectById(order.getPayOrderId()).getStatus());
        assertPrices(order, 100, 0);
    }

    @Test
    void failedPaymentWriteRollsBackTheOrderAndItsItemAllocations() {
        var order = order();
        jdbc.execute(
                "CREATE TRIGGER jss_price_pay_failure BEFORE UPDATE ON pay_order FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated payment failure'");
        try {
            assertThrows(RuntimeException.class, () -> adjust(order.getId(), 10));
        } finally {
            jdbc.execute("DROP TRIGGER jss_price_pay_failure");
        }
        assertPrices(order, 100, 0);
    }

    @Test
    void completedPaymentWithAnUnnotifiedTradeOrderRollsBackTheEntirePriceChange()
            throws Exception {
        var order = order();
        var executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            tx -> {
                                orders.selectById(order.getId());
                                items.selectListByOrderId(order.getId());
                                payments.selectById(order.getPayOrderId());
                                try {
                                    executor.submit(
                                                    () -> {
                                                        TenantContextHolder.setTenantId(1L);
                                                        try {
                                                            payments.updateById(
                                                                    new PayOrderDO()
                                                                            .setId(
                                                                                    order
                                                                                            .getPayOrderId())
                                                                            .setStatus(10));
                                                        } finally {
                                                            TenantContextHolder.clear();
                                                        }
                                                    })
                                            .get(20, TimeUnit.SECONDS);
                                } catch (Exception ex) {
                                    throw new RuntimeException(ex);
                                }
                                assertThrows(
                                        ServiceException.class, () -> adjust(order.getId(), -10));
                                tx.setRollbackOnly();
                            });
        } finally {
            executor.shutdownNow();
        }
        assertEquals(10, payments.selectById(order.getPayOrderId()).getStatus());
        assertPrices(order, 100, 0);
    }

    @Test
    void missingTradeOrPaymentReferencesAreBusinessErrorsWithoutPartialChanges() {
        assertThrows(ServiceException.class, () -> adjust(null, 10));
        assertThrows(ServiceException.class, () -> adjust(Long.MAX_VALUE, 10));
        var order = order();
        orders.updateById(new TradeOrderDO().setId(order.getId()).setPayOrderId(Long.MAX_VALUE));
        assertThrows(ServiceException.class, () -> adjust(order.getId(), 10));
        assertPrices(order, 100, 0);
    }

    @Test
    void failedItemWriteRollsBackTheHeaderAndLeavesPaymentUntouched() {
        var order = order();
        jdbc.execute(
                "CREATE TRIGGER jss_price_item_failure BEFORE UPDATE ON trade_order_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated item failure'");
        try {
            assertThrows(RuntimeException.class, () -> adjust(order.getId(), 10));
        } finally {
            jdbc.execute("DROP TRIGGER jss_price_item_failure");
        }
        assertPrices(order, 100, 0);
    }

    @Test
    void outerTransactionRollbackRestoresAllThreePrices() {
        var order = order();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        tx -> {
                            adjust(order.getId(), 10);
                            assertPrices(order, 110, 10);
                            tx.setRollbackOnly();
                        });
        assertPrices(order, 100, 0);
    }

    @Test
    void invalidAmountsAndZeroOrOverflowingTotalsAreBusinessErrors() {
        var order = order();
        for (Integer delta : Arrays.asList(null, 0, -100, Integer.MAX_VALUE, Integer.MIN_VALUE))
            assertThrows(ServiceException.class, () -> adjust(order.getId(), delta));
        for (Integer amount : Arrays.asList(null, 0, -1))
            assertThrows(
                    ServiceException.class,
                    () -> payService.updatePayOrderPrice(order.getPayOrderId(), amount));
        assertPrices(order, 100, 0);
    }
}
