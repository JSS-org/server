package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.util.List;

@Data
public class GymVerificationSettingsReqVO {
    @NotNull private Boolean enabled;
    @NotNull private Boolean requireCheckin;

    @NotNull
    @Size(max = 4)
    private List<@NotNull @Pattern(regexp = "miniapp|app|meituan|douyin") String> supportedChannels;

    @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d")
    private String allowedStart;

    @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d")
    private String allowedEnd;
}
