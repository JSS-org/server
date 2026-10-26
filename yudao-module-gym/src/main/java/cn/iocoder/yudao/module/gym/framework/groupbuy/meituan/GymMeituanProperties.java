package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/** 仅由服务端配置提供凭据；避免生成包含 signKey / token 的 toString。 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "yudao.gym.group-buy.meituan")
public class GymMeituanProperties {
    private boolean enabled;

    /** 核销、撤销和退款请求单独启用；默认仅允许查询请求。 */
    private boolean mutationsEnabled;

    private Long developerId;
    private String signKey;
    @Positive private int connectTimeoutMillis = 3000;
    @Positive private int readTimeoutMillis = 10000;
    private Map<Long, TenantAuthorization> tenants = new HashMap<>();

    @Getter
    @Setter
    public static class TenantAuthorization {
        private Map<Long, StoreAuthorization> stores = new HashMap<>();
    }

    @Getter
    @Setter
    public static class StoreAuthorization {
        private String appAuthToken;
        // 健身服务零售文档使用 58；旧餐饮接口仅接受显式配置的 1，禁止混用。
        private Integer businessId = 58;
        private String realOpBizCode;
    }
}
