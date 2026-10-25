package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import jakarta.validation.constraints.*;

import lombok.*;

public final class GymMobileCourseQuoteVO {
    private GymMobileCourseQuoteVO() {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Create extends GymMobileBookingVO.StrictRequest {
        @NotNull @Positive private Long storeId;
        @NotNull @Positive private Long productId;
        @Positive private Long memberId;
        @Positive private Long approvalId;
    }

    public record Quote(
            String id,
            String storeId,
            String productId,
            String memberId,
            String amount,
            String expiresAt,
            String status,
            String sharePath) {}
}
