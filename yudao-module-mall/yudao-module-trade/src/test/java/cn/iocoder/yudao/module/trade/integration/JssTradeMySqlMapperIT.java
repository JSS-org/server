package cn.iocoder.yudao.module.trade.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageUserDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.cart.CartDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateChargeDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryPickUpStoreDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.brokerage.BrokerageUserMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.cart.CartMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.config.TradeConfigMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.delivery.DeliveryExpressTemplateChargeMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.delivery.DeliveryPickUpStoreMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Runs only through the owned-MySQL wrapper; no Spring, H2 or mapper doubles. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/jss_trade_mapper_verify_[0-9a-f]+.*")
class JssTradeMySqlMapperIT {
    private static SqlSessionFactory sessions;

    @BeforeAll
    static void configureActualMappers() {
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(
                new Environment(
                        "jss-isolated-mysql",
                        new JdbcTransactionFactory(),
                        new UnpooledDataSource(
                                "com.mysql.cj.jdbc.Driver",
                                System.getenv("JSS_TEST_MYSQL_URL"),
                                System.getenv("JSS_TEST_MYSQL_USER"),
                                System.getenv("JSS_TEST_MYSQL_PASSWORD"))));
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfigUtils.setGlobalConfig(
                configuration,
                new GlobalConfig()
                        .setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO))
                        .setMetaObjectHandler(new DefaultDBFieldHandler())
                        .setSqlInjector(new DefaultSqlInjector()));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(
                new TenantLineInnerInterceptor(
                        new TenantDatabaseInterceptor(new TenantProperties())));
        configuration.addInterceptor(plugins);
        configuration.addMapper(CartMapper.class);
        configuration.addMapper(TradeOrderMapper.class);
        configuration.addMapper(TradeOrderItemMapper.class);
        configuration.addMapper(AfterSaleMapper.class);
        configuration.addMapper(TradeConfigMapper.class);
        configuration.addMapper(DeliveryExpressTemplateChargeMapper.class);
        configuration.addMapper(DeliveryPickUpStoreMapper.class);
        configuration.addMapper(BrokerageUserMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void cartQueriesAndBulkUpdatesEnforceMemberAndTenantOwnership() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(CartMapper.class);
            var cart = new CartDO().setUserId(10L).setSpuId(1L).setSkuId(1L).setCount(2);
            mapper.insert(cart);
            assertTrue(mapper.selectByUserIdAndSkuId(10L, 1L).getSelected());
            assertNull(mapper.selectById(cart.getId(), 11L));
            mapper.updateByIds(List.of(cart.getId()), 11L, new CartDO().setCount(99));
            assertEquals(2, mapper.selectById(cart.getId(), 10L).getCount());
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectByUserIdAndSkuId(10L, 1L));
            mapper.updateByIds(List.of(cart.getId()), 10L, new CartDO().setCount(99));
            TenantContextHolder.setTenantId(1L);
            assertEquals(2, mapper.selectById(cart.getId()).getCount());
            mapper.deleteById(cart.getId());
            assertNull(mapper.selectByUserIdAndSkuId(10L, 1L));
            assertEquals(
                    1,
                    mapper.insert(
                            new CartDO().setUserId(10L).setSpuId(1L).setSkuId(1L).setCount(1)));
        }
    }

    @Test
    void orderGiftFieldsAndStatusUpdatesKeepTenantAndMemberScope() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(TradeOrderMapper.class);
            var order =
                    new TradeOrderDO()
                            .setNo("O-isolated")
                            .setUserId(10L)
                            .setStatus(0)
                            .setAdjustPrice(-10)
                            .setGiveCouponIds(List.of(3L, 4L))
                            .setGiveCouponTemplateCounts(Map.of(7L, 2));
            mapper.insert(order);
            var loaded = mapper.selectByIdAndUserId(order.getId(), 10L);
            assertEquals(order.getGiveCouponIds(), loaded.getGiveCouponIds());
            assertEquals(order.getGiveCouponTemplateCounts(), loaded.getGiveCouponTemplateCounts());
            assertEquals(-10, loaded.getAdjustPrice());
            assertFalse(loaded.getPayStatus());
            assertNull(loaded.getPayTime());
            assertNull(mapper.selectByIdAndUserId(order.getId(), 11L));
            assertEquals(
                    1,
                    mapper.updateByIdAndStatus(order.getId(), 0, new TradeOrderDO().setStatus(10)));
            assertEquals(
                    0,
                    mapper.updateByIdAndStatus(order.getId(), 0, new TradeOrderDO().setStatus(20)));
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectByIdAndUserId(order.getId(), 10L));
            assertEquals(
                    0,
                    mapper.updateByIdAndStatus(
                            order.getId(), 10, new TradeOrderDO().setStatus(20)));
        }
    }

    @Test
    void orderItemPropertySnapshotsAndAfterSaleStatusUseCompareAndSet() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(TradeOrderItemMapper.class);
            var property =
                    new TradeOrderItemDO.Property()
                            .setPropertyId(1L)
                            .setPropertyName("颜色")
                            .setValueId(2L)
                            .setValueName("蓝色");
            var item =
                    new TradeOrderItemDO()
                            .setUserId(10L)
                            .setOrderId(1L)
                            .setSpuId(1L)
                            .setSkuId(1L)
                            .setCount(2)
                            .setProperties(List.of(property));
            mapper.insert(item);
            assertEquals(
                    item.getProperties(),
                    mapper.selectByIdAndUserId(item.getId(), 10L).getProperties());
            assertNull(mapper.selectByIdAndUserId(item.getId(), 11L));
            assertEquals(1, mapper.updateAfterSaleStatus(item.getId(), 0, 10, 1L));
            assertEquals(0, mapper.updateAfterSaleStatus(item.getId(), 0, 20, 2L));
            TenantContextHolder.setTenantId(2L);
            assertEquals(0, mapper.updateAfterSaleStatus(item.getId(), 10, 20, 2L));
            TenantContextHolder.setTenantId(1L);
            assertEquals(1L, mapper.selectById(item.getId()).getAfterSaleId());
            assertEquals(10, mapper.selectById(item.getId()).getAfterSaleStatus());
        }
    }

    @Test
    void afterSaleQualifiedPropertySnapshotAndAuditStateStayTenantScoped() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(AfterSaleMapper.class);
            var property = new TradeOrderItemDO.Property().setPropertyId(1L).setValueName("蓝色");
            var sale =
                    new AfterSaleDO()
                            .setNo("AS-isolated")
                            .setStatus(10)
                            .setUserId(10L)
                            .setOrderId(1L)
                            .setOrderItemId(1L)
                            .setSpuId(1L)
                            .setSkuId(1L)
                            .setProperties(List.of(property))
                            .setApplyPicUrls(List.of("https://example.invalid/proof.png"));
            mapper.insert(sale);
            var loaded = mapper.selectByIdAndUserId(sale.getId(), 10L);
            assertEquals(sale.getProperties(), loaded.getProperties());
            assertEquals(sale.getApplyPicUrls(), loaded.getApplyPicUrls());
            assertNull(loaded.getAuditTime());
            assertNull(loaded.getRefundTime());
            assertNull(mapper.selectByIdAndUserId(sale.getId(), 11L));
            assertEquals(
                    1,
                    mapper.updateByIdAndStatus(sale.getId(), 10, new AfterSaleDO().setStatus(20)));
            assertEquals(
                    0,
                    mapper.updateByIdAndStatus(sale.getId(), 10, new AfterSaleDO().setStatus(30)));
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectByIdAndUserId(sale.getId(), 10L));
            assertEquals(
                    0,
                    mapper.updateByIdAndStatus(sale.getId(), 20, new AfterSaleDO().setStatus(30)));
        }
    }

    @Test
    void configurationShippingCsvAndLocalTimeRoundTrip() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var configs = session.getMapper(TradeConfigMapper.class);
            var config =
                    new TradeConfigDO()
                            .setAfterSaleRefundReasons(List.of("isolated reason"))
                            .setBrokeragePosterUrls(List.of("https://example.invalid/poster.png"))
                            .setBrokerageWithdrawTypes(List.of(1, 2));
            configs.insert(config);
            var loaded = configs.selectById(config.getId());
            assertEquals(config.getAfterSaleRefundReasons(), loaded.getAfterSaleRefundReasons());
            assertEquals(config.getBrokeragePosterUrls(), loaded.getBrokeragePosterUrls());
            assertEquals(config.getBrokerageWithdrawTypes(), loaded.getBrokerageWithdrawTypes());
            assertFalse(loaded.getBrokerageEnabled());
            var charges = session.getMapper(DeliveryExpressTemplateChargeMapper.class);
            var charge =
                    new DeliveryExpressTemplateChargeDO()
                            .setTemplateId(1L)
                            .setAreaIds(List.of(110000, 120000))
                            .setStartCount(0.5)
                            .setExtraCount(0.25);
            charges.insert(charge);
            assertEquals(charge.getAreaIds(), charges.selectById(charge.getId()).getAreaIds());
            assertEquals(0.25, charges.selectById(charge.getId()).getExtraCount());
            var stores = session.getMapper(DeliveryPickUpStoreMapper.class);
            var store =
                    new DeliveryPickUpStoreDO()
                            .setName("isolated-store")
                            .setStatus(0)
                            .setOpeningTime(LocalTime.of(8, 30))
                            .setClosingTime(LocalTime.of(21, 0))
                            .setVerifyUserIds(List.of(10L, 11L));
            stores.insert(store);
            assertEquals(store.getOpeningTime(), stores.selectById(store.getId()).getOpeningTime());
            assertEquals(
                    store.getVerifyUserIds(), stores.selectById(store.getId()).getVerifyUserIds());
            TenantContextHolder.setTenantId(2L);
            assertNull(configs.selectById(config.getId()));
            assertNull(charges.selectById(charge.getId()));
            assertNull(stores.selectById(store.getId()));
        }
    }

    @Test
    void brokerageSupportsReversalDebtAndConcurrentGuardedUnfreeze() throws Exception {
        TenantContextHolder.setTenantId(1L);
        Long id = 77L;
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(BrokerageUserMapper.class);
            mapper.insert(
                    new BrokerageUserDO().setId(id).setBrokeragePrice(100).setFrozenPrice(50));
            assertEquals(1, mapper.updatePriceDecr(id, -120));
            assertEquals(-20, mapper.selectById(id).getBrokeragePrice());
        }
        Callable<Integer> release =
                () -> {
                    TenantContextHolder.setTenantId(1L);
                    try (var session = sessions.openSession(true)) {
                        return session.getMapper(BrokerageUserMapper.class)
                                .updateFrozenPriceDecrAndPriceIncr(id, -40);
                    } finally {
                        TenantContextHolder.clear();
                    }
                };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var results = workers.invokeAll(List.of(release, release));
            assertEquals(1, results.get(0).get() + results.get(1).get());
        }
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(BrokerageUserMapper.class);
            assertEquals(10, mapper.selectById(id).getFrozenPrice());
            assertEquals(20, mapper.selectById(id).getBrokeragePrice());
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectById(id));
            assertEquals(0, mapper.updatePriceDecr(id, -1));
        }
    }
}
