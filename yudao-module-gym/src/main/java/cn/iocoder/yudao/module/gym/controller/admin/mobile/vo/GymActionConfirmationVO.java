package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

import lombok.*;

public class GymActionConfirmationVO {
    @Getter
    @Setter
    public static class Request {
        @NotNull
        @Pattern(regexp = "open_door|open_locker")
        private String action;

        @NotNull @Positive private Long resourceId;
        @Positive private Long compartmentId;

        @NotNull
        @Pattern(regexp = "[0-9]{6}")
        private String verificationCode;

        @JsonAnySetter
        public void reject(String name, Object value) {
            throw exception(MOBILE_REQUEST_INVALID);
        }

        @Override
        public String toString() {
            return "ActionConfirmationRequest[redacted]";
        }
    }

    public record Result(String confirmationToken, String expiresAt) {
        @Override
        public String toString() {
            return "ActionConfirmationResult[redacted]";
        }
    }
}
