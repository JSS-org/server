package cn.iocoder.yudao.module.promotion.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.reward.RewardActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillConfigDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillProductDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.reward.RewardActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.seckill.seckillactivity.SeckillActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.seckill.seckillactivity.SeckillProductMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.seckill.seckillconfig.SeckillConfigMapper;

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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Runs only through the owned-MySQL wrapper; no Spring, H2 or mapper doubles. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/jss_campaign_mapper_verify_[0-9a-f]+.*")
class JssPromotionCampaignMySqlMapperIT {
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
        configuration.addMapper(CouponTemplateMapper.class);
        configuration.addMapper(RewardActivityMapper.class);
        configuration.addMapper(SeckillActivityMapper.class);
        configuration.addMapper(SeckillProductMapper.class);
        configuration.addMapper(SeckillConfigMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void couponCsvScopeAndConcurrentSingleClaimLimitUseProductionMapper() throws Exception {
        TenantContextHolder.setTenantId(1L);
        Long id;
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(CouponTemplateMapper.class);
            var template =
                    new CouponTemplateDO()
                            .setName("isolated-coupon")
                            .setStatus(0)
                            .setTotalCount(1)
                            .setTakeLimitCount(1)
                            .setProductScopeValues(List.of(11L, 12L));
            mapper.insert(template);
            id = template.getId();
            assertEquals(
                    template.getProductScopeValues(),
                    mapper.selectById(id).getProductScopeValues());
        }
        Callable<Integer> claim =
                () -> {
                    TenantContextHolder.setTenantId(1L);
                    try (var session = sessions.openSession(true)) {
                        return session.getMapper(CouponTemplateMapper.class).updateTakeCount(id, 1);
                    } finally {
                        TenantContextHolder.clear();
                    }
                };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var results = workers.invokeAll(List.of(claim, claim));
            assertEquals(1, results.get(0).get() + results.get(1).get());
        }
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(CouponTemplateMapper.class);
            assertEquals(1, mapper.selectById(id).getTakeCount());
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectById(id));
            assertEquals(0, mapper.updateTakeCount(id, 1));
        }
    }

    @Test
    void nestedRewardRulesKeepCouponMapAndCsvScope() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(RewardActivityMapper.class);
            var rule =
                    new RewardActivityDO.Rule()
                            .setLimit(100)
                            .setDiscountPrice(10)
                            .setFreeDelivery(true)
                            .setPoint(3)
                            .setGiveCouponTemplateCounts(Map.of(7L, 2));
            var activity =
                    new RewardActivityDO()
                            .setName("isolated-reward")
                            .setStatus(0)
                            .setProductScopeValues(List.of(11L, 12L))
                            .setRules(List.of(rule));
            mapper.insert(activity);
            var loaded = mapper.selectById(activity.getId());
            assertEquals(activity.getProductScopeValues(), loaded.getProductScopeValues());
            assertEquals(activity.getRules(), loaded.getRules());
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectById(activity.getId()));
            TenantContextHolder.setTenantId(1L);
            mapper.deleteById(activity.getId());
            assertNull(mapper.selectById(activity.getId()));
        }
    }

    @Test
    void seckillConfigListsRoundTripAndConcurrentSkuStockCannotOversell() throws Exception {
        TenantContextHolder.setTenantId(1L);
        Long id;
        try (var session = sessions.openSession(true)) {
            var configs = session.getMapper(SeckillConfigMapper.class);
            var config =
                    new SeckillConfigDO()
                            .setName("isolated-slot")
                            .setStatus(0)
                            .setStartTime("08:00")
                            .setEndTime("10:00")
                            .setSliderPicUrls(List.of("https://example.invalid/1.png"));
            configs.insert(config);
            assertEquals(
                    config.getSliderPicUrls(),
                    configs.selectById(config.getId()).getSliderPicUrls());
            var activities = session.getMapper(SeckillActivityMapper.class);
            var activity =
                    new SeckillActivityDO()
                            .setName("isolated-seckill")
                            .setStatus(0)
                            .setConfigIds(List.of(config.getId()))
                            .setStock(3);
            activities.insert(activity);
            assertEquals(
                    activity.getConfigIds(),
                    activities.selectById(activity.getId()).getConfigIds());
            var products = session.getMapper(SeckillProductMapper.class);
            var product =
                    new SeckillProductDO()
                            .setActivityId(activity.getId())
                            .setSpuId(1L)
                            .setSkuId(1L)
                            .setConfigIds(activity.getConfigIds())
                            .setStock(3);
            products.insert(product);
            id = product.getId();
            assertEquals(product.getConfigIds(), products.selectById(id).getConfigIds());
            assertEquals(
                    product.getConfigIds(),
                    products.selectByActivityIdAndSkuId(activity.getId(), 1L).getConfigIds());
        }
        Callable<Integer> consume =
                () -> {
                    TenantContextHolder.setTenantId(1L);
                    try (var session = sessions.openSession(true)) {
                        return session.getMapper(SeckillProductMapper.class).updateStockDecr(id, 2);
                    } finally {
                        TenantContextHolder.clear();
                    }
                };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var results = workers.invokeAll(List.of(consume, consume));
            assertEquals(1, results.get(0).get() + results.get(1).get());
        }
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(SeckillProductMapper.class);
            assertEquals(1, mapper.selectById(id).getStock());
            assertEquals(1, mapper.updateStockIncr(id, 2));
            assertEquals(3, mapper.selectById(id).getStock());
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectById(id));
            assertEquals(0, mapper.updateStockDecr(id, 1));
        }
    }
}
