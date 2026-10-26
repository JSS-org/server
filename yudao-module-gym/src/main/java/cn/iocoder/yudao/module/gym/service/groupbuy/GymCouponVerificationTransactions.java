package cn.iocoder.yudao.module.gym.service.groupbuy;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponPreviewVO.Request;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponVerificationVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.groupbuy.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymVerificationDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymCardTemplateMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.groupbuy.*;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.GymVerificationMapper;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses.Prepare;
import cn.iocoder.yudao.module.gym.service.card.GymCardService;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.mobile.*;
import cn.iocoder.yudao.module.gym.service.points.GymPointRedemptionAccess;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymCouponVerificationTransactions {
    @Resource private GymCouponVerificationMapper rows;
    @Resource private GymCouponPreviewMapper previews;
    @Resource private GymCouponPreviewTransactions previewTransactions;
    @Resource private GymMemberCouponConsumption consumption;
    @Resource private GymCouponCipher cipher;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymCardTemplateMapper templates;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessClock clock;
    @Resource private GymCouponVerificationAccess access;
    @Resource private GymCardService cards;
    @Resource private GymVerificationMapper verifications;
    @Resource private Validator validator;

    // Contains plaintext only while the worker is executing; never log or serialize this ticket.
    public record Ticket(
            Long id,
            Long tenant,
            Long store,
            String code,
            Long deal,
            Long group,
            String requestId,
            GymMemberCouponConsumption.Scope scope,
            GymCouponPreviewTransactions.Context context) {
        @Override
        public String toString() {
            return "CouponTicket[redacted]";
        }
    }

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private GymCouponVerificationDO locked(Long id) {
        return rows.selectOne(
                new LambdaQueryWrapper<GymCouponVerificationDO>()
                        .eq(GymCouponVerificationDO::getId, id)
                        .last("FOR UPDATE"));
    }

    private GymCouponPreviewDO preview(Long id) {
        var identity = identities.requireIdentity();
        var p =
                previews.selectOne(
                        new LambdaQueryWrapper<GymCouponPreviewDO>()
                                .eq(GymCouponPreviewDO::getId, id)
                                .eq(GymCouponPreviewDO::getUserId, identity.getUserId())
                                .eq(GymCouponPreviewDO::getMemberId, identity.getMemberId()));
        if (p == null) throw exception(COUPON_PREVIEW_INVALID);
        return p;
    }

    private String code(GymCouponPreviewDO p) {
        var value =
                cipher.open(
                        p.getCouponCiphertext(),
                        TenantContextHolder.getRequiredTenantId(),
                        p.getStoreId(),
                        p.getUserId());
        if (!Objects.equals(
                p.getCouponHash(),
                cipher.hash(TenantContextHolder.getRequiredTenantId(), p.getPlatform(), value)))
            throw exception(COUPON_PREVIEW_INVALID);
        return value;
    }

    private GymCouponPreviewTransactions.Context eligible(GymCouponPreviewDO p, String code) {
        if (!Boolean.TRUE.equals(p.getRedeemable()) || !p.getExpiresAt().isAfter(now()))
            throw exception(COUPON_PREVIEW_INVALID);
        var req = new Request();
        req.setStoreId(p.getStoreId());
        req.setPlatform(p.getPlatform());
        req.setCouponCode(code);
        var context = previewTransactions.begin(req);
        var card = templates.selectById(p.getCardTemplateId());
        if (!Objects.equals(p.getSettingsRevision(), context.settingsRevision())
                || !Objects.equals(
                        p.getMappingFingerprint(),
                        cn.hutool.crypto.SecureUtil.sha256(context.mappingSnapshot()))
                || !GymPointRedemptionAccess.applicable(card, p.getStoreId())
                || !Objects.equals(p.getTemplateSnapshot(), JsonUtils.toJsonString(card)))
            throw exception(COUPON_PREVIEW_INVALID);
        return context;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long prepare(Create req, String key) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        identities.requireIdentity();
        return idempotency.execute(
                "coupon-verification",
                key,
                req,
                Long.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    var identity = identities.requireIdentity();
                    var prior =
                            rows.selectOne(
                                    new LambdaQueryWrapper<GymCouponVerificationDO>()
                                            .eq(
                                                    GymCouponVerificationDO::getUserId,
                                                    identity.getUserId())
                                            .eq(GymCouponVerificationDO::getRequestKey, key)
                                            .last("FOR UPDATE"));
                    if (prior != null) {
                        if (!Objects.equals(prior.getPreviewId(), req.getPreviewId())
                                || !Objects.equals(prior.getMemberId(), identity.getMemberId()))
                            throw exception(MOBILE_IDEMPOTENCY_CONFLICT);
                        return prior.getId();
                    }
                    var p = preview(req.getPreviewId());
                    prior =
                            rows.selectOne(
                                    new LambdaQueryWrapper<GymCouponVerificationDO>()
                                            .eq(GymCouponVerificationDO::getPreviewId, p.getId()));
                    if (prior != null) return prior.getId();
                    String code = code(p);
                    eligible(p, code);
                    var scope =
                            consumption.requireConsumptionScope(
                                    TenantContextHolder.getRequiredTenantId(), p.getStoreId());
                    if (rows.selectCount(
                                    new LambdaQueryWrapper<GymCouponVerificationDO>()
                                            .eq(
                                                    GymCouponVerificationDO::getPlatform,
                                                    p.getPlatform())
                                            .eq(
                                                    GymCouponVerificationDO::getClaimedCouponHash,
                                                    p.getCouponHash()))
                            > 0) throw exception(COUPON_ALREADY_CLAIMED);
                    var row = new GymCouponVerificationDO();
                    row.setUserId(p.getUserId());
                    row.setMemberId(p.getMemberId());
                    row.setStoreId(p.getStoreId());
                    row.setPreviewId(p.getId());
                    row.setRequestKey(key);
                    row.setProviderRequestId("gym-coupon-" + UUID.randomUUID());
                    row.setPlatform(p.getPlatform());
                    row.setCouponHash(p.getCouponHash());
                    row.setClaimedCouponHash(p.getCouponHash());
                    row.setDealId(p.getDealId());
                    row.setDealGroupId(p.getDealGroupId());
                    row.setCardTemplateId(p.getCardTemplateId());
                    row.setTemplateSnapshot(p.getTemplateSnapshot());
                    row.setScopeSnapshot(JsonUtils.toJsonString(scope));
                    row.setStatus("processing");
                    row.setStage("prepared");
                    row.setRequestedAt(now());
                    row.setNextCheckAt(now().plusSeconds(30));
                    row.setQueryCount(0);
                    rows.insert(row);
                    return row.getId();
                });
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Ticket inspect(Long id) {
        var row = rows.selectById(id);
        if (row == null
                || !"prepared".equals(row.getStage())
                || !"processing".equals(row.getStatus())) return null;
        var p = preview(row.getPreviewId());
        String code = code(p);
        var context = eligible(p, code);
        return ticket(row, code, context);
    }

    private Ticket ticket(
            GymCouponVerificationDO row,
            String code,
            GymCouponPreviewTransactions.Context context) {
        return new Ticket(
                row.getId(),
                TenantContextHolder.getRequiredTenantId(),
                row.getStoreId(),
                code,
                row.getDealId(),
                row.getDealGroupId(),
                row.getProviderRequestId(),
                JsonUtils.parseObject(
                        row.getScopeSnapshot(), GymMemberCouponConsumption.Scope.class),
                context);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public boolean start(Ticket before, Prepare prepared) {
        mutex.lockStoreEntitlements();
        var row = locked(before.id());
        if (row == null
                || !"processing".equals(row.getStatus())
                || !"prepared".equals(row.getStage())) return false;
        var p = preview(row.getPreviewId());
        String code = code(p);
        var context = eligible(p, code);
        if (!ticket(row, code, context).equals(before)
                || !consumption
                        .requireConsumptionScope(before.tenant(), before.store())
                        .equals(before.scope())
                || prepared == null
                || !Objects.equals(code, prepared.getReceiptCode())
                || !Objects.equals(row.getDealId(), prepared.getDealId())
                || !Objects.equals(row.getDealGroupId(), prepared.getDealGroupId())
                || GymCouponPreviewTransactions.unavailableReason(prepared, now()) != null)
            throw exception(COUPON_PREVIEW_INVALID);
        row.setStage("sending");
        row.setConsumeStartedAt(now());
        row.setNextCheckAt(now().plusSeconds(30));
        rows.updateById(row);
        return true;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void confirm(Long id, GymMemberCouponConsumption.Confirmation proof) {
        mutex.lockStoreEntitlements();
        var row = locked(id);
        if (row == null) throw exception(COUPON_VERIFICATION_INVALID);
        GymCouponVerificationAccess.proof(row, proof);
        if ("confirmed".equals(row.getStage()) || "fulfilled".equals(row.getStage())) {
            if (!Objects.equals(row.getConfirmationSnapshot(), JsonUtils.toJsonString(proof)))
                throw exception(COUPON_VERIFICATION_INVALID);
            return;
        }
        if (!"processing".equals(row.getStatus())
                || !Set.of("sending", "unknown").contains(row.getStage()))
            throw exception(COUPON_VERIFICATION_INVALID);
        row.setConfirmationSnapshot(JsonUtils.toJsonString(proof));
        row.setProviderFlowId(proof.flowId());
        row.setConfirmedAt(now());
        row.setStage("confirmed");
        row.setNextCheckAt(now());
        rows.updateById(row);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void reject(Long id, Integer code, boolean dispatched) {
        mutex.lockStoreEntitlements();
        var row = locked(id);
        if (row == null
                || !"processing".equals(row.getStatus())
                || !(dispatched
                        ? Set.of("sending", "unknown").contains(row.getStage())
                        : "prepared".equals(row.getStage()))) return;
        if (dispatched
                && !Set.of(
                                GROUP_BUY_PROVIDER_UNCONFIGURED.getCode(),
                                GROUP_BUY_PROVIDER_SCENE_MISMATCH.getCode(),
                                GROUP_BUY_PROVIDER_MUTATIONS_DISABLED.getCode(),
                                GROUP_BUY_PROVIDER_REJECTED.getCode())
                        .contains(code)) throw exception(COUPON_VERIFICATION_INVALID);
        row.setStatus("failed");
        row.setStage("rejected");
        row.setLastErrorCode(code);
        row.setCompletedAt(now());
        row.setClaimedCouponHash(null);
        rows.updateById(row);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void defer(Long id, Integer code) {
        mutex.lockStoreEntitlements();
        var row = locked(id);
        if (row == null || !"processing".equals(row.getStatus())) return;
        if ("sending".equals(row.getStage())) row.setStage("unknown");
        row.setLastErrorCode(code);
        row.setNextCheckAt(now().plusSeconds(60));
        rows.updateById(row);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public boolean claimObservation(Long id) {
        mutex.lockStoreEntitlements();
        var row = locked(id);
        if (row == null
                || !"processing".equals(row.getStatus())
                || !Set.of("sending", "unknown").contains(row.getStage())
                || row.getNextCheckAt().isAfter(now())
                || row.getQueryCount() >= 6) return false;
        // Commit the bounded query lease before any network I/O, including scope/decryption
        // failure.
        row.setQueryCount(row.getQueryCount() + 1);
        row.setStage("unknown");
        row.setNextCheckAt(now().plusSeconds(60));
        rows.updateById(row);
        return true;
    }

    @Transactional(readOnly = true)
    public Ticket observationTicket(Long id) {
        var row = rows.selectById(id);
        if (row == null
                || !"processing".equals(row.getStatus())
                || !"unknown".equals(row.getStage())) return null;
        var p = previews.selectById(row.getPreviewId());
        if (p == null
                || !Objects.equals(p.getUserId(), row.getUserId())
                || !Objects.equals(p.getMemberId(), row.getMemberId())
                || !Objects.equals(p.getStoreId(), row.getStoreId())
                || !Objects.equals(p.getCouponHash(), row.getCouponHash()))
            throw exception(COUPON_VERIFICATION_INVALID);
        return ticket(row, code(p), null);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void observed(Long id, GymMemberCouponConsumption.Observation observation) {
        mutex.lockStoreEntitlements();
        var row = locked(id);
        if (row == null
                || !"processing".equals(row.getStatus())
                || !"unknown".equals(row.getStage())) return;
        if (!Objects.equals(row.getCouponHash(), observation.couponHash())
                || !Objects.equals(row.getDealId(), observation.dealId())
                || !Objects.equals(row.getDealGroupId(), observation.dealGroupId()))
            throw exception(COUPON_VERIFICATION_INVALID);
        row.setObservationSnapshot(JsonUtils.toJsonString(observation));
        rows.updateById(row);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void fulfill(Long id) {
        mutex.lockStoreEntitlements();
        var row = locked(id);
        if (row == null
                || !"processing".equals(row.getStatus())
                || !"confirmed".equals(row.getStage())) return;
        var allowed = access.require(id);
        var card = cards.issueCouponVerification(id);
        var verification = new GymVerificationDO();
        verification.setStoreId(row.getStoreId());
        verification.setMemberId(row.getMemberId());
        verification.setMemberName(allowed.member().getName());
        verification.setType("card");
        verification.setChannel("meituan");
        verification.setStatus("succeeded");
        verification.setProviderReference(row.getProviderFlowId());
        verification.setRequestKey("coupon-verification:" + id);
        verification.setMemberCardId(card.getId());
        verification.setVerifiedAt(row.getConfirmedAt());
        verifications.insert(verification);
        row.setMemberCardId(card.getId());
        row.setVerificationId(verification.getId());
        row.setStatus("succeeded");
        row.setStage("fulfilled");
        row.setCompletedAt(now());
        rows.updateById(row);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result get(Long id) {
        var identity = identities.requireIdentity();
        if (id == null || id <= 0) throw exception(COUPON_VERIFICATION_NOT_EXISTS);
        var row =
                rows.selectOne(
                        new LambdaQueryWrapper<GymCouponVerificationDO>()
                                .eq(GymCouponVerificationDO::getId, id)
                                .eq(GymCouponVerificationDO::getUserId, identity.getUserId())
                                .eq(GymCouponVerificationDO::getMemberId, identity.getMemberId()));
        if (row == null) throw exception(COUPON_VERIFICATION_NOT_EXISTS);
        String reason =
                "failed".equals(row.getStatus())
                        ? "本次未完成核销，请重新预览或联系门店"
                        : "processing".equals(row.getStatus())
                                ? ("confirmed".equals(row.getStage())
                                        ? "团购券已核销，会员卡正在发放，请勿重复核销"
                                        : "核销结果待确认，请勿重复核销；请查询本记录或联系门店")
                                : null;
        return new Result(
                row.getId().toString(),
                row.getStatus(),
                row.getMemberCardId() == null
                        ? List.of()
                        : List.of(row.getMemberCardId().toString()),
                reason);
    }

    public List<Long> due() {
        return rows
                .selectList(
                        new LambdaQueryWrapper<GymCouponVerificationDO>()
                                .eq(GymCouponVerificationDO::getStatus, "processing")
                                .le(GymCouponVerificationDO::getNextCheckAt, now())
                                .and(
                                        w ->
                                                w.in(
                                                                GymCouponVerificationDO::getStage,
                                                                List.of("prepared", "confirmed"))
                                                        .or()
                                                        .lt(
                                                                GymCouponVerificationDO
                                                                        ::getQueryCount,
                                                                6))
                                .orderByAsc(
                                        GymCouponVerificationDO::getNextCheckAt,
                                        GymCouponVerificationDO::getId)
                                .last("LIMIT 100"))
                .stream()
                .map(GymCouponVerificationDO::getId)
                .toList();
    }
}
