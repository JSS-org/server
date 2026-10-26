package cn.iocoder.yudao.module.gym.framework.mobile;

import jakarta.validation.constraints.*;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "yudao.gym.mobile.invitations")
@Validated
@Data
public class GymMobileInvitationProperties {
    /** Reserved integration path; registering the frontend page is separate work. */
    @NotNull
    @Size(max = 200)
    @Pattern(regexp = "/pages/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")
    private String sharePage = "/pages/invitation/index";

    @Min(1)
    @Max(365)
    private int validDays = 30;
}
