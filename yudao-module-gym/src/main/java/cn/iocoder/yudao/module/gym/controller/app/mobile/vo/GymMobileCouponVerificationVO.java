package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class GymMobileCouponVerificationVO {
    @Getter
    @Setter
    public static class Create {
        @NotNull @Positive private Long previewId;

        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID);
        }
    }

    public record Result(
            String id, String status, List<String> memberCardIds, String failureReason) {}
}
