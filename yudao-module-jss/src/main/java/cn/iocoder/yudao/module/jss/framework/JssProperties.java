package cn.iocoder.yudao.module.jss.framework;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jss.workspace")
@Data
public class JssProperties {
    /** Rollout is opt-in; disabling also invalidates every existing workspace request. */
    private boolean enabled = false;
}
