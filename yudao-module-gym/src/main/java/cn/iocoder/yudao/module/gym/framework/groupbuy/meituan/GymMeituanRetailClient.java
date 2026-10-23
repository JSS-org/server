package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanResponse;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.*;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 服务零售团购核销目录的全部 16 个 POST 接口；不执行本地发卡、财务或自动重试。 */
@Component
@Validated
public class GymMeituanRetailClient {
    @Resource private GymMeituanTransport transport;

    public MeituanResponse<Prepare> prepare(
            Long tenantId, Long storeId, @Valid MeituanReceiptCodeRequest request) {
        return transport.post(tenantId, storeId, MeituanApi.PREPARE, request, Prepare.class);
    }

    public MeituanResponse<Consume> consume(
            Long tenantId, Long storeId, @Valid MeituanConsumeRequest request) {
        return transport.post(tenantId, storeId, MeituanApi.CONSUME, request, Consume.class);
    }

    public MeituanResponse<ReverseConsume> reverseConsume(
            Long tenantId, Long storeId, @Valid MeituanReverseConsumeRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.REVERSE_CONSUME, request, ReverseConsume.class);
    }

    public MeituanResponse<Consumed> getConsumed(
            Long tenantId, Long storeId, @Valid MeituanReceiptCodeRequest request) {
        return transport.post(tenantId, storeId, MeituanApi.GET_CONSUMED, request, Consumed.class);
    }

    public MeituanResponse<History> history(
            Long tenantId, Long storeId, @Valid MeituanReceiptHistoryRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.RECEIPT_HISTORY, request, History.class);
    }

    public MeituanResponse<Deals> shopDeals(
            Long tenantId, Long storeId, @Valid MeituanShopDealsRequest request) {
        return transport.post(tenantId, storeId, MeituanApi.SHOP_DEALS, request, Deals.class);
    }

    public MeituanResponse<ProductsByType> productsByType(
            Long tenantId, Long storeId, @Valid MeituanProductsByTypeRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.PRODUCTS_BY_TYPE, request, ProductsByType.class);
    }

    public MeituanResponse<Products> products(
            Long tenantId, Long storeId, @Valid MeituanProductsRequest request) {
        return transport.post(tenantId, storeId, MeituanApi.PRODUCTS, request, Products.class);
    }

    public MeituanResponse<MobileReceipts> mobileReceipts(
            Long tenantId, Long storeId, @Valid MeituanMobileReceiptsRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.MOBILE_RECEIPTS, request, MobileReceipts.class);
    }

    public MeituanResponse<BatchConsume> batchConsume(
            Long tenantId, Long storeId, @Valid MeituanBatchConsumeRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.BATCH_CONSUME, request, BatchConsume.class);
    }

    public MeituanResponse<StandardProducts> standardProducts(Long tenantId, Long storeId) {
        return transport.post(
                tenantId, storeId, MeituanApi.STANDARD_PRODUCTS, null, StandardProducts.class);
    }

    public MeituanResponse<RefundPrecheck> refundPrecheck(
            Long tenantId, Long storeId, @Valid MeituanRefundPrecheckRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.REFUND_PRECHECK, request, RefundPrecheck.class);
    }

    public MeituanResponse<RefundApply> refundApply(
            Long tenantId, Long storeId, @Valid MeituanRefundApplyRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.REFUND_APPLY, request, RefundApply.class);
    }

    public MeituanResponse<RefundQuery> refundQuery(
            Long tenantId, Long storeId, @Valid MeituanRefundQueryRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.REFUND_QUERY, request, RefundQuery.class);
    }

    public MeituanResponse<RefundAudit> refundAudit(
            Long tenantId, Long storeId, @Valid MeituanRefundAuditRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.REFUND_AUDIT, request, RefundAudit.class);
    }

    public MeituanResponse<ReverseConsume> batchReverseConsume(
            Long tenantId, Long storeId, @Valid MeituanBatchReverseConsumeRequest request) {
        return transport.post(
                tenantId, storeId, MeituanApi.BATCH_REVERSE_CONSUME, request, ReverseConsume.class);
    }
}
