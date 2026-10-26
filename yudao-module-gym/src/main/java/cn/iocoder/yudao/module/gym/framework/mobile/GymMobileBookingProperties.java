package cn.iocoder.yudao.module.gym.framework.mobile;

import jakarta.validation.constraints.*;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "yudao.gym.mobile.bookings")
@Validated
@Data
public class GymMobileBookingProperties {
    /** Self-service cancellation stops this many minutes before class; no automatic forfeiture. */
    @Min(0)
    @Max(10080)
    private int cancelBeforeMinutes = 0;
}
