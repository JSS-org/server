package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class GymMeituanConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GymMeituanProperties.class)
    static class Config {}

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
