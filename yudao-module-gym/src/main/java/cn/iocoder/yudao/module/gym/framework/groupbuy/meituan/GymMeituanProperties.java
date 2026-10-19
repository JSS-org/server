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
        // 当前接口文档为到店餐饮 businessId=1；其他行业必须另行确认接口。
        private Integer businessId = 1;
        private String realOpBizCode;
    }
}
