package cn.iocoder.yudao.module.gym.controller.admin.groupbuy;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.*;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanResponse;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.*;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses.*;
import cn.iocoder.yudao.module.gym.service.groupbuy.GymMeituanRetailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 美团服务零售团购核销")
@RestController
@RequestMapping("/gym/group-buy/meituan/retail")
@Validated
public class GymMeituanRetailController {
    @Resource private GymMeituanRetailService service;

    @PostMapping("/receipts/prepare")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    @Operation(summary = "输码验券校验")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<Prepare>> prepare(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanReceiptCodeRequest> req) {
        return success(service.prepare(req));
    }

    @PostMapping("/receipts/consume")
    @PreAuthorize("@ss.hasPermission('gym:verifications:manage')")
    @Operation(summary = "验券")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<Consume>> consume(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanConsumeRequest> req) {
        return success(service.consume(req));
    }

    @PostMapping("/receipts/reverse-consume")
    @PreAuthorize("@ss.hasPermission('gym:verifications:manage')")
    @Operation(summary = "撤销验券")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<ReverseConsume>> reverseConsume(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanReverseConsumeRequest> req) {
        return success(service.reverseConsume(req));
    }

    @PostMapping("/receipts/consumed/query")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    @Operation(summary = "查询已验券信息")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<Consumed>> getConsumed(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanReceiptCodeRequest> req) {
        return success(service.getConsumed(req));
    }

    @PostMapping("/receipts/history/query")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    @Operation(summary = "验券记录")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<History>> history(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanReceiptHistoryRequest> req) {
        return success(service.history(req));
    }

    @PostMapping("/deals/query")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    @Operation(summary = "获取团购信息")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<Deals>> shopDeals(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanShopDealsRequest> req) {
        return success(service.shopDeals(req));
    }

    @PostMapping("/products/by-type/query")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    @Operation(summary = "查询门店商品信息（带 type）")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<ProductsByType>> productsByType(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanProductsByTypeRequest> req) {
        return success(service.productsByType(req));
    }

    @PostMapping("/products/query")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    @Operation(summary = "按商品类型查询门店商品信息（文档实际不带 type）")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<Products>> products(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanProductsRequest> req) {
        return success(service.products(req));
    }

    @PostMapping("/receipts/by-mobile/query")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    @Operation(summary = "手机号查询可用团购券")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<MobileReceipts>> mobileReceipts(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanMobileReceiptsRequest> req) {
        return success(service.mobileReceipts(req));
    }

    @PostMapping("/receipts/batch-consume")
    @PreAuthorize("@ss.hasPermission('gym:verifications:manage')")
    @Operation(summary = "次卡批量验券")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<BatchConsume>> batchConsume(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanBatchConsumeRequest> req) {
        return success(service.batchConsume(req));
    }

    @PostMapping("/standard-products/query")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    @Operation(summary = "查询标品信息")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<StandardProducts>> standardProducts(
            @Valid @RequestBody GymMeituanStoreReqVO req) {
        return success(service.standardProducts(req));
    }

    @PostMapping("/refunds/precheck")
    @PreAuthorize("@ss.hasPermission('gym:group-buy:refund')")
    @Operation(summary = "团购退款预校验")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<RefundPrecheck>> refundPrecheck(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanRefundPrecheckRequest> req) {
        return success(service.refundPrecheck(req));
    }

    @PostMapping("/refunds/apply")
    @PreAuthorize("@ss.hasPermission('gym:group-buy:refund')")
    @Operation(summary = "团购退款申请")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<RefundApply>> refundApply(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanRefundApplyRequest> req) {
        return success(service.refundApply(req));
    }

    @PostMapping("/refunds/query")
    @PreAuthorize("@ss.hasPermission('gym:group-buy:refund')")
    @Operation(summary = "团购退款信息查询")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<RefundQuery>> refundQuery(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanRefundQueryRequest> req) {
        return success(service.refundQuery(req));
    }

    @PostMapping("/refunds/audit")
    @PreAuthorize("@ss.hasPermission('gym:group-buy:refund')")
    @Operation(summary = "团购退款审核")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<RefundAudit>> refundAudit(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanRefundAuditRequest> req) {
        return success(service.refundAudit(req));
    }

    @PostMapping("/receipts/batch-reverse-consume")
    @PreAuthorize("@ss.hasPermission('gym:verifications:manage')")
    @Operation(summary = "批量撤销验券")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MeituanResponse<ReverseConsume>> batchReverseConsume(
            @Valid @RequestBody GymMeituanRetailReqVO<MeituanBatchReverseConsumeRequest> req) {
        return success(service.batchReverseConsume(req));
    }
}
