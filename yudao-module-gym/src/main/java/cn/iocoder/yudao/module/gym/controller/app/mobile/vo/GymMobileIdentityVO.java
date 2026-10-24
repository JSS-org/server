package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;

import lombok.Data;

public final class GymMobileIdentityVO {
    private GymMobileIdentityVO() {}

    @Data
    public static class Initialize {
        @NotNull @Positive private Long storeId;

        @NotBlank
        @Size(max = 64)
        private String source;

        @Size(max = 128)
        private String inviteCode;
    }

    @Data
    public static class ProfilePatch {
        @Size(min = 1, max = 30)
        private String nickname;

        @Positive private Long avatarFileId;
        @JsonIgnore private boolean avatarPresent;

        public void setAvatarFileId(Long value) {
            avatarFileId = value;
            avatarPresent = true;
        }

        @JsonAnySetter
        public void unknown(String name, Object value) {
            if ("phone".equals(name) || "mobile".equals(name))
                throw exception(MOBILE_PHONE_IMMUTABLE);
            throw exception(MOBILE_REQUEST_INVALID);
        }
    }

    public record Initialization(String memberId, String customerType, boolean created) {}

    public record Context(String memberId, String defaultStoreId, boolean profileComplete) {}

    public record Profile(
            String memberId,
            String nickname,
            String avatarFileId,
            String maskedPhone,
            String faceStatus) {}
}
