package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.*;

public final class GymMobileRefundVO {
    private GymMobileRefundVO() {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Create extends GymMobileBookingVO.StrictRequest {
        @NotBlank
        @Size(max = 500)
        private String reason;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Decision extends GymMobileBookingVO.StrictRequest {
        @NotNull
        @Min(1)
        private Integer expectedVersion;

        @NotNull
        @Pattern(regexp = "approve|reject")
        private String decision;

        /** Rejection notes are visible to the member. */
        @NotBlank
        @Size(max = 500)
        private String note;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @Positive private Long storeId;
        @Positive private Long orderId;

        @Pattern(regexp = "pending_review|approved|partially_refunded|refunded|rejected|closed")
        private String status;
    }

    public record Submitted(String id, String status, String requestedAmount) {}

    public record Application(
            String id,
            String orderId,
            String status,
            String requestedAmount,
            String refundedAmount,
            String reason,
            String failureReason) {}

    public record Review(
            String id,
            String orderId,
            String storeId,
            String memberId,
            String status,
            String requestedAmount,
            String refundedAmount,
            String reason,
            String failureReason,
            int version,
            String submittedAt,
            String reviewedAt,
            String reviewNote) {}
}
