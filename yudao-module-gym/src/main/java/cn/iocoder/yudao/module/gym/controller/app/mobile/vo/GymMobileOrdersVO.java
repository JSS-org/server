package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.*;

import java.util.List;

public final class GymMobileOrdersVO {
    private GymMobileOrdersVO() {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Create extends GymMobileBookingVO.StrictRequest {
        @NotNull @Positive private Long storeId;

        @NotNull
        @Pattern(regexp = "card|course")
        private String productType;

        @NotNull @Positive private Long productId;

        @NotNull
        @Min(1)
        @Max(1)
        private Integer quantity;

        @Positive private Long quoteId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @Pattern(regexp = "unpaid|paid|closed|partially_refunded|refunded")
        private String status;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Cancel extends GymMobileBookingVO.StrictRequest {
        @Size(max = 500)
        private String reason;
    }

    public record Order(
            String id,
            String orderNo,
            String storeId,
            String productType,
            String productId,
            String productName,
            int quantity,
            String payableAmount,
            String paidAmount,
            String refundedAmount,
            String paymentStatus,
            String fulfillmentStatus,
            String expiresAt,
            List<String> memberCardIds,
            List<String> coursePackIds) {}
}
