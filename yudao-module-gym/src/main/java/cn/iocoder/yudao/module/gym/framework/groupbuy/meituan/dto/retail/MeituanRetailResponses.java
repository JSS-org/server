package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** 服务零售团购核销响应模型；金额按平台各字段单位保留，不推算本地收入或履约结果。 */
public final class MeituanRetailResponses {
    private MeituanRetailResponses() {}

    @Getter
    @Setter
    public static class PaymentDetail {
        private String paymentDetailId;
        private String amount;
        private Integer amountType;
    }

    @Getter
    @Setter
    public static class AdditionItem {
        private String serviceTitle;
        private String adjustedAmount;
        private String productId;
        private String addItemThirdPartyId;
    }

    @Getter
    @Setter
    public static class RelatedReceipt {
        private String receiptCode;
        private String dealPrice;
        private String platformAmount;
        private String merchantAmount;
        private String orderId;
        private Boolean tgTimesCardFlag;
        private String purchaseToConsumeRatio;
        private String timesCardUnitPrice;
    }

    @Getter
    @Setter
    public static class Receipt {
        private String receiptCode;
        private Long dealId;

        @JsonAlias("dealgroupId")
        private Long dealGroupId;

        private String dealTitle;
        private BigDecimal dealPrice;
        private BigDecimal dealMarketPrice;
        private String mobile;
        private Integer bizType;
        private Long productType;
        private Long productItemId;
        private Long receiptEndDate;
        private Boolean tgTimesCardFlag;
        private String purchaseToConsumeRatio;
        private String timesCardUnitPrice;
        private String platformAmount;
        private String merchantAmount;
        private List<PaymentDetail> paymentDetail;
        private List<AdditionItem> additionItemInfos;
        private Integer dealQuantity;
    }

    @Getter
    @Setter
    public static class Prepare extends Receipt {
        private Integer count;
        private String thirdPartyProductId;
        private String receiptInfoMap;
        private Integer dealType;
        private String opUserId;
        private Integer platform;
        private Long buyTime;
        private Integer verificationType;
        private List<RelatedReceipt> relatedReceiptInfo;
    }

    @Getter
    @Setter
    public static class ConsumeDetail extends Receipt {
        private String orderId;
        private String productSnapshot;
        private String flowId;
    }

    @Getter
    @Setter
    public static class Consume {
        private List<ConsumeDetail> result;
        private List<RelatedReceipt> relatedReceiptInfo;
    }

    @Getter
    @Setter
    public static class ReverseConsume {
        private List<ConsumeDetail> result;
    }

    @Getter
    @Setter
    public static class ShopPromo {
        private BigDecimal promoAmount;
        private Integer promoType;
        private String desc;
    }

    @Getter
    @Setter
    public static class Consumed extends Receipt {
        private BigDecimal orderShopPromoAmount;
        private List<ShopPromo> orderShopPromoDetails;
        private String verifyAccount;
        private String verifyChannel;
        private String verifyTime;
        private Boolean bookVerifyGroup;
        private String orderId;
        private String userId;
    }

    @Getter
    @Setter
    public static class HistoryRecord extends Receipt {
        private String verifyAccount;
        private String verifyChannel;
        private String verifyTime;
        private String flowId;
        private Integer refundStatus;
        private Boolean bookVerifyGroup;
    }

    @Getter
    @Setter
    public static class History {
        private Integer totalCount;
        private List<HistoryRecord> records;
    }

    @Getter
    @Setter
    public static class Deal {
        private Long dealId;
        private Long dealGroupId;
        private String beginDate;
        private String endDate;
        private String title;
        private BigDecimal price;
        private BigDecimal marketPrice;
        private String receiptBeginDate;
        private String receiptEndDate;
        private Integer saleStatus;
        private Integer dealGroupStatus;
        private String saleChannelName;
        private String dealType;
    }

    @Getter
    @Setter
    public static class Deals {
        private List<Deal> result;
    }

    @Getter
    @Setter
    public static class ServiceSku {
        private Long skuId;
        private Long skuIdLong;
        private String skuName;
        private Integer quantity;
        private String price;
    }

    @Getter
    @Setter
    public static class ProductItem {
        private Long productItemId;
        private Long productItemIdLong;
        private Long productId;
        private Long productIdLong;
        private String name;
        private String price;
        private String marketPrice;
        private Long type;
        private List<ServiceSku> serviceSkuList;
    }

    @Getter
    @Setter
    public static class Product {
        private Long productId;
        private Long productIdLong;
        private Long type;
        private List<ProductItem> productItemList;
    }

    @Getter
    @Setter
    public static class ProductsByType {
        private List<Product> result;
    }

    @Getter
    @Setter
    public static class Products {
        private List<ProductItem> result;
    }

    @Getter
    @Setter
    public static class UserReceipt {
        private String serialNumber;
    }

    @Getter
    @Setter
    public static class MobileReceipts {
        private List<UserReceipt> result;
    }

    @Getter
    @Setter
    public static class SingleConsume {
        private Integer singleConsumeCode;
        private String singleConsumeMsg;
        private List<ConsumeDetail> consumeDetailList;
    }

    @Getter
    @Setter
    public static class BatchConsume {
        private List<SingleConsume> result;
    }

    @Getter
    @Setter
    public static class StandardProduct {
        private Long platformProductSkuId;
        private Long spuId;
        private String cityName;
        private String platformProductName;
        private BigDecimal platformProductPrice;
    }

    @Getter
    @Setter
    public static class StandardProducts {
        private List<StandardProduct> result;
    }

    @Getter
    @Setter
    public static class RefundReceipt {
        private String receiptId;
        private String receiptCode;
        private Long receiptVerifyShopId;
        private String receiptVerifyShopName;
        private Long receiptVerifyDate;
    }

    @Getter
    @Setter
    public static class RefundAmountItem {
        private Integer amountType;
        private String title;
        private Long value;
        private Boolean refundable;
    }

    @Getter
    @Setter
    public static class PreRefundResult {
        private Boolean canRefund;
        private Integer maxRefundQuantity;
        // 预校验字段表明确为 object，与查询接口的 array 不同。
        private RefundReceipt receiptList;
        private RefundAmountItem refundAmountItemList;
        private Boolean canPartRefund;
        private Long maxRefundAmount;
    }

    @Getter
    @Setter
    public static class RefundPrecheck {
        private PreRefundResult result;
    }

    @Getter
    @Setter
    public static class RefundApplyResult {
        private String refundId;
        private Long settleRefundAmount;
    }

    @Getter
    @Setter
    public static class RefundApply {
        private RefundApplyResult result;
    }

    @Getter
    @Setter
    public static class RefundQueryResult {
        private Boolean canRefund;
        private Integer maxRefundQuantity;
        private List<RefundReceipt> receiptList;
        private List<RefundAmountItem> refundAmountItemList;
        private Boolean canPartRefund;
        private Long maxRefundAmount;
    }

    @Getter
    @Setter
    public static class RefundQuery {
        private RefundQueryResult result;
    }

    @Getter
    @Setter
    public static class RefundAuditResult {
        private Long settleRefundAmount;
    }

    @Getter
    @Setter
    public static class RefundAudit {
        private RefundAuditResult result;
    }
}
