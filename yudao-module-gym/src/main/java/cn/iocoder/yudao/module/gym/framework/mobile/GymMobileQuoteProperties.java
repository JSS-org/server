package cn.iocoder.yudao.module.gym.framework.mobile;

import jakarta.validation.constraints.*;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "yudao.gym.mobile.quotes")
@Validated
@Data
public class GymMobileQuoteProperties {
    /** Reserved integration path; the frontend must register its own quote detail page. */
    @NotNull
    @Size(max = 200)
    @Pattern(regexp = "/pages/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")
    private String sharePage = "/pages/course-quote/detail";
}
