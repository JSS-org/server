package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileCouponProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class GymMeituanConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({GymMeituanProperties.class, GymMobileCouponProperties.class})
    static class Config {}

    @Test
    void memberPreviewEnablementLeavesConsumptionDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("yudao.gym.mobile.coupons.enabled=true")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            var coupons = context.getBean(GymMobileCouponProperties.class);
                            assertTrue(coupons.isEnabled());
                            assertFalse(coupons.isConsumptionEnabled());
                            assertNull(coupons.merchant(7L, 12L));
                        });
    }

    @Test
    void memberMerchantAuditAccountBindsOnlyToExplicitTenantAndStore() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues(
                        "yudao.gym.mobile.coupons.consumption-enabled=true",
                        "yudao.gym.mobile.coupons.merchant-accounts[7][12].account=merchant-12",
                        "yudao.gym.mobile.coupons.merchant-accounts[7][12].name=Store self-service")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            var coupons = context.getBean(GymMobileCouponProperties.class);
                            assertTrue(coupons.isConsumptionEnabled());
                            assertFalse(coupons.isEnabled());
                            assertEquals("merchant-12", coupons.merchant(7L, 12L).getAccount());
                            assertEquals("Store self-service", coupons.merchant(7L, 12L).getName());
                            assertNull(coupons.merchant(8L, 12L));
                            assertNull(coupons.merchant(7L, 13L));
                        });
    }

    @Test
    void blankEnvironmentDefaultKeepsIntegrationDisabledAndApplicationBootable() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("yudao.gym.group-buy.meituan.developer-id=")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            var properties = context.getBean(GymMeituanProperties.class);
                            assertFalse(properties.isEnabled());
                            assertNull(properties.getDeveloperId());
                            assertNotNull(new GymMeituanCouponClient(properties));
                        });
    }

    @Test
    void bindsCredentialsByTenantAndStore() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues(
                        "yudao.gym.group-buy.meituan.tenants[7].stores[12].app-auth-token=test-only",
                        "yudao.gym.group-buy.meituan.tenants[7].stores[12].real-op-biz-code=shop-12")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            var tenant =
                                    context.getBean(GymMeituanProperties.class)
                                            .getTenants()
                                            .get(7L);
                            assertEquals(
                                    "test-only", tenant.getStores().get(12L).getAppAuthToken());
                            assertEquals("shop-12", tenant.getStores().get(12L).getRealOpBizCode());
                            assertEquals(58, tenant.getStores().get(12L).getBusinessId());
                        });
    }
}
