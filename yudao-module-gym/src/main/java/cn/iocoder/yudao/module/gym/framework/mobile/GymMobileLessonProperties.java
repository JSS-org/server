package cn.iocoder.yudao.module.gym.framework.mobile;

import jakarta.validation.constraints.*;

import lombok.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "yudao.gym.mobile.lessons")
@Validated
@Data
public class GymMobileLessonProperties {
    @ToString.Exclude private String qrSigningKey;

    @Min(30)
    @Max(300)
    private int qrTtlSeconds = 60;

    @Min(0)
    @Max(1440)
    private int confirmAfterEndMinutes = 120;
}
