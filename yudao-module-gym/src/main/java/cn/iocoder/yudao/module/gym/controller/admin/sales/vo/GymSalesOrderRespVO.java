package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Data
public class GymSalesOrderRespVO {
    private String id;
    private String storeId;
    private String storeName;
    private String memberId;
    private String memberName;
    private String orderNo;
    private String channel;
    private String status;
    private BigDecimal payableAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private Instant orderedAt;
    private Boolean approvalRequired;
    private Boolean contractRequired;
    private Instant fulfilledAt;
    private List<String> memberCardIds;
    private GymFulfillmentTaskRespVO fulfillmentTask;
    private List<GymRefundReviewRespVO> refundReviews;
    private List<Item> items;
    private List<Payment> payments;
    private List<Refund> refunds;

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymSalesOrderRespVOItem")
    public static class Item {
        private String id;
        private String productType;
        private String productId;
        private String productNameSnapshot;
        private Integer productVersion;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal payableAmount;
    }

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymSalesOrderRespVOPayment")
    public static class Payment {
        private String id;
        private BigDecimal amount;
        private String status;
        private Instant paidAt;
        private String failureReason;
    }

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymSalesOrderRespVORefund")
    public static class Refund {
        private String id;
        private String paymentId;
        private BigDecimal amount;
        private String status;
        private Instant refundedAt;
        private String failureReason;
    }
}
