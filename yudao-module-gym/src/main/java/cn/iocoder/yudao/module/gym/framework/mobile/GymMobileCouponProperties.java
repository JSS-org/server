package cn.iocoder.yudao.module.gym.framework.mobile;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "yudao.gym.mobile.coupons")
public class GymMobileCouponProperties {
    private boolean enabled;
    private String encryptionKey;
    // tenant -> store -> Meituan deal ID. No implicit cross-store defaults.
    private Map<Long, Map<Long, Map<Long, Mapping>>> mappings = new HashMap<>();

    @Getter
    @Setter
    public static class Mapping {
        private boolean enabled;
        private Long dealGroupId;
        private Long cardTemplateId;
    }

    public Map<Long, Mapping> store(Long tenant, Long store) {
        var tenantRows = mappings == null ? null : mappings.get(tenant);
        var rows = tenantRows == null ? null : tenantRows.get(store);
        return rows == null ? Map.of() : rows;
    }
}
