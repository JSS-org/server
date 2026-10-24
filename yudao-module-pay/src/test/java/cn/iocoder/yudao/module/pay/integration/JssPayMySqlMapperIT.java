package cn.iocoder.yudao.module.pay.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.transfer.PayTransferDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.wallet.PayWalletDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.wallet.PayWalletTransactionDO;
import cn.iocoder.yudao.module.pay.dal.mysql.channel.PayChannelMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.order.PayOrderExtensionMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.transfer.PayTransferMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.wallet.PayWalletMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.wallet.PayWalletTransactionMapper;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.NonePayClientConfig;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Owned MySQL only; production mappers/handlers, without Spring or payment providers. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/jss_pay_mapper_verify_[0-9a-f]+.*")
class JssPayMySqlMapperIT {
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
        configuration.addMapper(PayWalletMapper.class);
        configuration.addMapper(PayWalletTransactionMapper.class);
        configuration.addMapper(PayChannelMapper.class);
        configuration.addMapper(PayOrderExtensionMapper.class);
        configuration.addMapper(PayTransferMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private PayWalletDO newWallet(PayWalletMapper mapper, long userId) {
        var wallet = new PayWalletDO().setUserId(userId).setUserType(1).setBalance(100);
        assertEquals(1, mapper.insert(wallet));
        return wallet;
    }

    @Test
    void concurrentConsumptionCannotOverdrawWallet() throws Exception {
        TenantContextHolder.setTenantId(1L);
        Long id;
        try (var session = sessions.openSession(true)) {
            id = newWallet(session.getMapper(PayWalletMapper.class), 101).getId();
        }
        Callable<Integer> consume =
                () -> {
                    TenantContextHolder.setTenantId(1L);
                    try (var session = sessions.openSession(true)) {
                        return session.getMapper(PayWalletMapper.class).updateWhenConsumption(id, 80);
                    } finally {
                        TenantContextHolder.clear();
                    }
                };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var results = workers.invokeAll(List.of(consume, consume));
            assertEquals(1, results.get(0).get() + results.get(1).get());
        }
        try (var session = sessions.openSession(true)) {
            var wallet = session.getMapper(PayWalletMapper.class).selectById(id);
            assertEquals(20, wallet.getBalance());
            assertEquals(80, wallet.getTotalExpense());
        }
    }

    @Test
    void walletLookupsAndUpdatesEnforceTenantAndLogicalDelete() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(PayWalletMapper.class);
            var wallet = newWallet(mapper, 102);
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectByUserIdAndType(102L, 1));
            assertEquals(0, mapper.updateWhenConsumption(wallet.getId(), 1));
            assertEquals(0, mapper.freezePrice(wallet.getId(), 1));
            TenantContextHolder.setTenantId(1L);
            assertEquals(100, mapper.selectByUserIdAndType(102L, 1).getBalance());
            assertEquals(1, mapper.deleteById(wallet.getId()));
            assertNull(mapper.selectByUserIdAndType(102L, 1));
            assertEquals(0, mapper.updateWhenRecharge(wallet.getId(), 1));
        }
    }

    @Test
    void rechargeRefundRequiresBothFrozenFundsAndRechargeTotal() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(PayWalletMapper.class);
            var wallet = newWallet(mapper, 103);
            var id = wallet.getId();
            assertEquals(1, mapper.freezePrice(id, 80));
            assertEquals(0, mapper.freezePrice(id, 30));
            assertEquals(0, mapper.updateWhenRechargeRefund(id, 50));
            assertEquals(1, mapper.updateWhenRecharge(id, 60));
            assertEquals(0, mapper.updateWhenRechargeRefund(id, 70));
            assertEquals(1, mapper.updateWhenRechargeRefund(id, 60));
            assertEquals(0, mapper.unFreezePrice(id, 30));
            assertEquals(1, mapper.unFreezePrice(id, 20));
            var loaded = mapper.selectById(id);
            assertEquals(100, loaded.getBalance());
            assertEquals(0, loaded.getFreezePrice());
            assertEquals(0, loaded.getTotalRecharge());
        }
    }

    @Test
    void signedTransactionsAndBusinessLookupRemainTenantScoped() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(PayWalletTransactionMapper.class);
            var debit = new PayWalletTransactionDO().setNo("W-isolated-1").setWalletId(1L)
                    .setBizId("isolated-biz").setBizType(1).setTitle("debit").setPrice(-80).setBalance(20);
            assertEquals(1, mapper.insert(debit));
            assertEquals(-80, mapper.selectByBiz("isolated-biz", 1).getPrice());
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectByNo(debit.getNo()));
            assertNull(mapper.selectByBiz("isolated-biz", 1));
        }
    }

    @Test
    void channelConfigAndPaymentExtrasRoundTripThroughProductionHandlers() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var channels = session.getMapper(PayChannelMapper.class);
            var channel = new PayChannelDO().setAppId(1L).setCode("isolated")
                    .setStatus(1).setConfig(new NonePayClientConfig());
            assertEquals(1, channels.insert(channel));
            assertInstanceOf(NonePayClientConfig.class, channels.selectById(channel.getId()).getConfig());
            var extensions = session.getMapper(PayOrderExtensionMapper.class);
            var extension = new PayOrderExtensionDO().setNo("P-isolated-1").setOrderId(1L)
                    .setChannelId(channel.getId()).setStatus(0).setChannelExtras(Map.of("scene", "H5"))
                    .setChannelNotifyData("<xml>untrusted callback text</xml>");
            assertEquals(1, extensions.insert(extension));
            var loaded = extensions.selectByNo(extension.getNo());
            assertEquals(extension.getChannelExtras(), loaded.getChannelExtras());
            assertEquals(extension.getChannelNotifyData(), loaded.getChannelNotifyData());
            assertEquals(1, extensions.updateByIdAndStatus(extension.getId(), 0, new PayOrderExtensionDO().setStatus(10)));
            assertEquals(0, extensions.updateByIdAndStatus(extension.getId(), 0, new PayOrderExtensionDO().setStatus(20)));
            TenantContextHolder.setTenantId(2L);
            assertNull(channels.selectById(channel.getId()));
            assertNull(extensions.selectByNo(extension.getNo()));
        }
    }

    @Test
    void transferRetryClearsPreviousChannelResultWithStatusGuard() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(PayTransferMapper.class);
            var transfer = new PayTransferDO().setNo("T-isolated-1").setAppId(1L)
                    .setMerchantTransferId("isolated-merchant").setSubject("isolated transfer")
                    .setPrice(50).setStatus(20).setUserName("old name").setNotifyUrl("https://example.invalid")
                    .setChannelExtras(Map.of("old", "value")).setChannelTransferNo("old-channel")
                    .setSuccessTime(LocalDateTime.now()).setChannelErrorCode("old-error")
                    .setChannelErrorMsg("old message").setChannelNotifyData("old response")
                    .setChannelPackageInfo("old-package");
            assertEquals(1, mapper.insert(transfer));
            var retry = new PayTransferDO().setStatus(0);
            assertEquals(0, mapper.updateByIdAndStatusAndClearChannelResult(transfer.getId(), 10, retry));
            assertEquals("old-package", mapper.selectById(transfer.getId()).getChannelPackageInfo());
            assertEquals(1, mapper.updateByIdAndStatusAndClearChannelResult(transfer.getId(), 20, retry));
            var loaded = mapper.selectByAppIdAndMerchantOrderId(1L, transfer.getMerchantTransferId());
            assertEquals(0, loaded.getStatus());
            assertNull(loaded.getSuccessTime());
            assertNull(loaded.getChannelTransferNo());
            assertNull(loaded.getChannelErrorCode());
            assertNull(loaded.getChannelErrorMsg());
            assertNull(loaded.getChannelNotifyData());
            assertNull(loaded.getChannelPackageInfo());
            assertNull(loaded.getUserName());
            assertNull(loaded.getNotifyUrl());
            assertNull(loaded.getChannelExtras());
            assertEquals(1, mapper.updateChannelPackageInfoIfAbsent(transfer.getId(), "new-package"));
            assertEquals(0, mapper.updateChannelPackageInfoIfAbsent(transfer.getId(), "stale-package"));
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectByAppIdAndMerchantOrderId(1L, transfer.getMerchantTransferId()));
            assertEquals(0, mapper.updateChannelPackageInfoIfAbsent(transfer.getId(), "foreign"));
        }
    }
}
