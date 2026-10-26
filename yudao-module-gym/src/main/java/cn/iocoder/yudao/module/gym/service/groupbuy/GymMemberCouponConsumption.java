package cn.iocoder.yudao.module.gym.service.groupbuy;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanProperties;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailClient;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanConsumeRequest;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanReceiptCodeRequest;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses.Receipt;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileCouponProperties;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Internal single-voucher provider boundary, not an authorization or durable workflow.
 * A caller must persist its claim and fixed request ID before invoking consume exactly once.
 */
@Service
public class GymMemberCouponConsumption {
    @Resource private GymMeituanRetailClient meituan;
    @Resource private GymMeituanProperties provider;
    @Resource private GymMobileCouponProperties mobile;
    @Resource private GymCouponCipher cipher;

    public record Scope(String fingerprint, String merchantAccount, String merchantName) {
        @Override
        public String toString() {
            return "CouponScope[redacted]";
        }
    }

    /** Proof comes only from the synchronous response to this exact consume request. */
    public record Confirmation(
            String couponHash,
            Long dealId,
            Long dealGroupId,
            String requestId,
            String authorizationFingerprint,
            String flowId,
            String orderId,
            String traceId) {}

    /** This query cannot correlate a consumed voucher with our request ID. Never use it to issue cards. */
    public record Observation(
            String couponHash, Long dealId, Long dealGroupId, String orderId, String traceId) {}

    public Scope requireConsumptionScope(Long tenant, Long store) {
        if (!mobile.isEnabled() || !mobile.isConsumptionEnabled())
            throw exception(COUPON_NOT_CONFIGURED);
        if (!provider.isMutationsEnabled()) throw exception(GROUP_BUY_PROVIDER_MUTATIONS_DISABLED);
        return scope(tenant, store);
    }

    private Scope scope(Long tenant, Long store) {
        if (tenant == null || tenant <= 0 || store == null || store <= 0)
            throw exception(COUPON_PREVIEW_INVALID);
        var tenantAuth = provider.getTenants() == null ? null : provider.getTenants().get(tenant);
        var auth =
                tenantAuth == null || tenantAuth.getStores() == null
                        ? null
                        : tenantAuth.getStores().get(store);
        if (!provider.isEnabled()
                || provider.getDeveloperId() == null
                || provider.getDeveloperId() <= 0
                || !StringUtils.hasText(provider.getSignKey())
                || auth == null
                || !StringUtils.hasText(auth.getAppAuthToken()))
            throw exception(GROUP_BUY_PROVIDER_UNCONFIGURED);
        if (!Integer.valueOf(58).equals(auth.getBusinessId()))
            throw exception(GROUP_BUY_PROVIDER_SCENE_MISMATCH);
        var merchant = mobile.merchant(tenant, store);
        if (merchant == null
                || !identifier(merchant.getAccount(), 128)
                || !identifier(merchant.getName(), 128)) throw exception(COUPON_NOT_CONFIGURED);
        // JSON array avoids delimiter ambiguity. Sign-key rotation is also a scope change.
        String fingerprint =
                cipher.authorizationFingerprint(
                        JsonUtils.toJsonString(
                                List.of(
                                        tenant,
                                        store,
                                        provider.getDeveloperId(),
                                        provider.getSignKey(),
                                        auth.getAppAuthToken(),
                                        auth.getBusinessId(),
                                        StringUtils.hasText(auth.getRealOpBizCode())
                                                ? auth.getRealOpBizCode()
                                                : "",
                                        merchant.getAccount(),
                                        merchant.getName())));
        return new Scope(fingerprint, merchant.getAccount(), merchant.getName());
    }

    private void input(String code, Long deal, Long group, Scope expected) {
        if (code == null
                || !code.matches("[A-Za-z0-9]{1,128}")
                || deal == null
                || deal <= 0
                || group == null
                || group <= 0
                || expected == null) throw exception(COUPON_PREVIEW_INVALID);
    }

    private boolean identifier(String value, int max) {
        return StringUtils.hasText(value)
                && value.length() <= max
                && value.equals(value.strip())
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private boolean matches(Receipt row, String code, Long deal, Long group) {
        return row != null
                && code.equals(row.getReceiptCode())
                && deal.equals(row.getDealId())
                && group.equals(row.getDealGroupId())
                && Integer.valueOf(0).equals(row.getBizType())
                && (row.getDealQuantity() == null
                        || Integer.valueOf(1).equals(row.getDealQuantity()));
    }

    @Transactional(propagation = Propagation.NEVER)
    public Confirmation consume(
            Long tenant,
            Long store,
            String code,
            Long deal,
            Long group,
            String requestId,
            Scope expected) {
        input(code, deal, group, expected);
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,128}"))
            throw exception(COUPON_PREVIEW_INVALID);
        Scope current = requireConsumptionScope(tenant, store);
        if (!current.equals(expected)) throw exception(COUPON_PREVIEW_INVALID);
        var request = new MeituanConsumeRequest();
        request.setReceiptCode(code);
        request.setCount(1);
        request.setRequestId(requestId);
        request.setAppShopAccount(current.merchantAccount());
        request.setAppShopAccountName(current.merchantName());
        try {
            var response = meituan.consume(tenant, store, request);
            if (response == null
                    || !"OP_SUCCESS".equals(response.getCode())
                    || response.getData() == null
                    || response.getData().getResult() == null
                    || response.getData().getResult().size() != 1
                    || (response.getData().getRelatedReceiptInfo() != null
                            && !response.getData().getRelatedReceiptInfo().isEmpty()))
                throw exception(GROUP_BUY_PROVIDER_RESULT_UNKNOWN);
            var row = response.getData().getResult().getFirst();
            if (!matches(row, code, deal, group)
                    || !Boolean.FALSE.equals(row.getTgTimesCardFlag())
                    || !identifier(row.getFlowId(), 128)
                    || !identifier(row.getOrderId(), 128))
                throw exception(GROUP_BUY_PROVIDER_RESULT_UNKNOWN);
            return new Confirmation(
                    cipher.hash(tenant, "meituan", code),
                    deal,
                    group,
                    requestId,
                    current.fingerprint(),
                    row.getFlowId(),
                    row.getOrderId(),
                    trace(response.getTraceId()));
        } catch (ServiceException e) {
            // Only transport's explicit pre-dispatch/public-parameter rejections are definitive.
            if (Objects.equals(e.getCode(), GROUP_BUY_PROVIDER_UNCONFIGURED.getCode())
                    || Objects.equals(e.getCode(), GROUP_BUY_PROVIDER_SCENE_MISMATCH.getCode())
                    || Objects.equals(e.getCode(), GROUP_BUY_PROVIDER_MUTATIONS_DISABLED.getCode())
                    || Objects.equals(e.getCode(), GROUP_BUY_PROVIDER_REJECTED.getCode())) throw e;
            throw exception(GROUP_BUY_PROVIDER_RESULT_UNKNOWN);
        } catch (RuntimeException e) {
            // No vendor message, response body, credential or coupon in the local exception.
            throw exception(GROUP_BUY_PROVIDER_RESULT_UNKNOWN);
        }
    }

    @Transactional(propagation = Propagation.NEVER)
    public Observation observe(
            Long tenant, Long store, String code, Long deal, Long group, Scope expected) {
        input(code, deal, group, expected);
        // Stop new consumption without preventing read-only investigation of an existing attempt.
        if (!scope(tenant, store).equals(expected)) throw exception(COUPON_PREVIEW_INVALID);
        var request = new MeituanReceiptCodeRequest();
        request.setReceiptCode(code);
        var response = meituan.getConsumed(tenant, store, request);
        if (response == null
                || !"OP_SUCCESS".equals(response.getCode())
                || !matches(response.getData(), code, deal, group)
                || !identifier(response.getData().getOrderId(), 128))
            throw exception(COUPON_PROVIDER_INVALID);
        return new Observation(
                cipher.hash(tenant, "meituan", code),
                deal,
                group,
                response.getData().getOrderId(),
                trace(response.getTraceId()));
    }

    private String trace(String value) {
        return identifier(value, 128) ? value : null;
    }
}
