package cn.iocoder.yudao.module.gym.service.groupbuy;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.*;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailClient;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanResponse;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.*;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class GymMeituanRetailService {
    @Resource private GymStoreService stores;
    @Resource private GymMeituanRetailClient client;

    private Long prepare(Long storeId, Object request) {
        stores.requireStore(storeId, false);
        if (request instanceof MeituanAccountRequest || request instanceof MeituanOperatorRequest) {
            var user = SecurityFrameworkUtils.getLoginUser();
            if (user == null || user.getId() == null) throw exception(UNAUTHORIZED);
            String account = user.getId().toString();
            String name =
                    user.getInfo() == null ? null : user.getInfo().get(LoginUser.INFO_KEY_NICKNAME);
            if (!StringUtils.hasText(name)) name = account;
            if (request instanceof MeituanAccountRequest r) {
                r.setAppShopAccount(account);
                r.setAppShopAccountName(name);
            }
            if (request instanceof MeituanOperatorRequest r) r.setOperatorId(account);
            if (request instanceof MeituanRefundAuditRequest r) {
                r.setOperatorName(name);
                r.setOperatorIp(ServletUtils.getClientIP());
                r.setOperatorMobile(null); // 当前身份上下文未提供手机号，不接受调用方冒充。
            }
        }
        return TenantContextHolder.getRequiredTenantId();
    }

    public MeituanResponse<Prepare> prepare(
            @Valid GymMeituanRetailReqVO<MeituanReceiptCodeRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.prepare(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<Consume> consume(
            @Valid GymMeituanRetailReqVO<MeituanConsumeRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.consume(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<ReverseConsume> reverseConsume(
            @Valid GymMeituanRetailReqVO<MeituanReverseConsumeRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.reverseConsume(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<Consumed> getConsumed(
            @Valid GymMeituanRetailReqVO<MeituanReceiptCodeRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.getConsumed(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<History> history(
            @Valid GymMeituanRetailReqVO<MeituanReceiptHistoryRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.history(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<Deals> shopDeals(
            @Valid GymMeituanRetailReqVO<MeituanShopDealsRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.shopDeals(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<ProductsByType> productsByType(
            @Valid GymMeituanRetailReqVO<MeituanProductsByTypeRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.productsByType(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<Products> products(
            @Valid GymMeituanRetailReqVO<MeituanProductsRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.products(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<MobileReceipts> mobileReceipts(
            @Valid GymMeituanRetailReqVO<MeituanMobileReceiptsRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.mobileReceipts(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<BatchConsume> batchConsume(
            @Valid GymMeituanRetailReqVO<MeituanBatchConsumeRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.batchConsume(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<StandardProducts> standardProducts(@Valid GymMeituanStoreReqVO req) {
        Long tenantId = prepare(req.getStoreId(), null);
        return client.standardProducts(tenantId, req.getStoreId());
    }

    public MeituanResponse<RefundPrecheck> refundPrecheck(
            @Valid GymMeituanRetailReqVO<MeituanRefundPrecheckRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.refundPrecheck(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<RefundApply> refundApply(
            @Valid GymMeituanRetailReqVO<MeituanRefundApplyRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.refundApply(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<RefundQuery> refundQuery(
            @Valid GymMeituanRetailReqVO<MeituanRefundQueryRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.refundQuery(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<RefundAudit> refundAudit(
            @Valid GymMeituanRetailReqVO<MeituanRefundAuditRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.refundAudit(tenantId, req.getStoreId(), req.getBiz());
    }

    public MeituanResponse<ReverseConsume> batchReverseConsume(
            @Valid GymMeituanRetailReqVO<MeituanBatchReverseConsumeRequest> req) {
        Long tenantId = prepare(req.getStoreId(), req.getBiz());
        return client.batchReverseConsume(tenantId, req.getStoreId(), req.getBiz());
    }
}
