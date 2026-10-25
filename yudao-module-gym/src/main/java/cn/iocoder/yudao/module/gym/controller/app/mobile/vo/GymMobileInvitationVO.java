package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import jakarta.validation.constraints.*;

import lombok.*;

public final class GymMobileInvitationVO {
    private GymMobileInvitationVO() {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Create extends GymMobileBookingVO.StrictRequest {
        @NotNull @Positive private Long storeId;
    }

    public record Invitation(String inviteCode, String sharePath, String expiresAt) {}
}
