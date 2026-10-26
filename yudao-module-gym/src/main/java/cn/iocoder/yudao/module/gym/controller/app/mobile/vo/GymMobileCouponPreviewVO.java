package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class GymMobileCouponPreviewVO {
    @Getter
    @Setter
    public static class Request {
        @NotNull @Positive private Long storeId;

        @NotBlank
        @Pattern(regexp = "meituan")
        private String platform;

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9]{1,128}")
        private String couponCode;

        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID);
        }
    }

    public record Preview(
            String previewId,
            String productName,
            boolean redeemable,
            String reason,
            String expiresAt) {}
}
