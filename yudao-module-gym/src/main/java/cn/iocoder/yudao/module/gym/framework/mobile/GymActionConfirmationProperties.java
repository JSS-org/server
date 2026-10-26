package cn.iocoder.yudao.module.gym.framework.mobile;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "yudao.gym.mobile.action-confirmations")
public class GymActionConfirmationProperties {
    private boolean enabled;
    // Explicit tenant -> administrator -> Base32 secret. Never supply a default credential.
    private Map<Long, Map<Long, String>> secrets = new HashMap<>();

    public String secret(Long tenant, Long user) {
        var users = secrets == null ? null : secrets.get(tenant);
        return !enabled || users == null ? null : users.get(user);
    }

    @Override
    public String toString() {
        return "GymActionConfirmationProperties[redacted]";
    }
}
