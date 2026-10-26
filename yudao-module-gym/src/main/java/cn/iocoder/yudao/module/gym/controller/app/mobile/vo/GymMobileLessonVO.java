package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.StrictRequest;

import jakarta.validation.constraints.*;

import lombok.*;

public final class GymMobileLessonVO {
    private GymMobileLessonVO() {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileLessonQrCreate")
    public static class QrCreate extends StrictRequest {
        @NotNull @Positive private Long sessionId;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileLessonConfirmation")
    public static class Confirm extends StrictRequest {
        @NotNull @Positive private Long bookingId;

        @NotBlank
        @Size(max = 128)
        private String qrToken;
    }

    public record Qr(String qrToken, String expiresAt) {}
}
