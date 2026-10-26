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
    // Preview enablement must never implicitly authorize a provider mutation.
    private boolean consumptionEnabled;
    private String encryptionKey;
    // tenant -> store -> Meituan deal ID. No implicit cross-store defaults.
    private Map<Long, Map<Long, Map<Long, Mapping>>> mappings = new HashMap<>();
    // Explicit merchant audit identity, never inferred from the member account.
    private Map<Long, Map<Long, MerchantAccount>> merchantAccounts = new HashMap<>();

    @Getter
    @Setter
    public static class MerchantAccount {
        private String account;
        private String name;
    }

    public MerchantAccount merchant(Long tenant, Long store) {
        var rows = merchantAccounts == null ? null : merchantAccounts.get(tenant);
        return rows == null ? null : rows.get(store);
    }

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
