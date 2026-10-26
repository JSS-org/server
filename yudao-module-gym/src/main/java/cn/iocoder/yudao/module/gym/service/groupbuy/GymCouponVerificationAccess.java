package cn.iocoder.yudao.module.gym.service.groupbuy;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.groupbuy.GymCouponVerificationDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.groupbuy.GymCouponVerificationMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessMutex;
import cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileIdentityService;
import cn.iocoder.yudao.module.gym.service.points.GymPointRedemptionAccess;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.Objects;

@Service
public class GymCouponVerificationAccess {
    @Resource private GymCouponVerificationMapper rows;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberMapper members;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymMemberRecordsService records;
    @Resource private GymBusinessMutex mutex;

    public record Verification(
            GymCouponVerificationDO row,
            GymMemberDO member,
            GymStoreDO store,
            GymCardTemplateDO card) {}

    public static void proof(
            GymCouponVerificationDO row, GymMemberCouponConsumption.Confirmation p) {
        GymMemberCouponConsumption.Scope scope;
        try {
            scope =
                    JsonUtils.parseObject(
                            row.getScopeSnapshot(), GymMemberCouponConsumption.Scope.class);
        } catch (RuntimeException e) {
            throw exception(COUPON_VERIFICATION_INVALID);
        }
        if (p == null
                || scope == null
                || !Objects.equals(row.getCouponHash(), p.couponHash())
                || !Objects.equals(row.getDealId(), p.dealId())
                || !Objects.equals(row.getDealGroupId(), p.dealGroupId())
                || !Objects.equals(row.getProviderRequestId(), p.requestId())
                || !Objects.equals(scope.fingerprint(), p.authorizationFingerprint())
                || p.flowId() == null
                || p.flowId().isBlank()
                || p.flowId().length() > 128
                || p.orderId() == null
                || p.orderId().isBlank()
                || p.orderId().length() > 128) throw exception(COUPON_VERIFICATION_INVALID);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Verification require(Long id) {
        mutex.lockStoreEntitlements();
        var identity = identities.requireIdentity();
        var row =
                rows.selectOne(
                        new LambdaQueryWrapper<GymCouponVerificationDO>()
                                .eq(GymCouponVerificationDO::getId, id)
                                .last("FOR UPDATE"));
        if (row == null
                || !identity.getUserId().equals(row.getUserId())
                || !identity.getMemberId().equals(row.getMemberId())
                || !"processing".equals(row.getStatus())
                || !"confirmed".equals(row.getStage())
                || row.getConfirmedAt() == null) throw exception(COUPON_VERIFICATION_INVALID);
        GymMemberCouponConsumption.Confirmation p;
        GymCardTemplateDO card;
        try {
            p =
                    JsonUtils.parseObject(
                            row.getConfirmationSnapshot(),
                            GymMemberCouponConsumption.Confirmation.class);
            card = JsonUtils.parseObject(row.getTemplateSnapshot(), GymCardTemplateDO.class);
        } catch (RuntimeException e) {
            throw exception(COUPON_VERIFICATION_INVALID);
        }
        proof(row, p);
        if (!Objects.equals(row.getProviderFlowId(), p.flowId())
                || !GymPointRedemptionAccess.applicable(card, row.getStoreId())
                || !Objects.equals(row.getCardTemplateId(), card.getId())
                || !Objects.equals(card.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(COUPON_VERIFICATION_INVALID);
        var store = stores.selectForUpdate(row.getStoreId());
        var member = members.selectForUpdate(row.getMemberId());
        if (store == null
                || !"active".equals(store.getStatus())
                || member == null
                || !"active".equals(member.getStatus())
                || memberships.selectCount(
                                new LambdaQueryWrapper<GymMemberStoreDO>()
                                        .eq(GymMemberStoreDO::getMemberId, member.getId())
                                        .eq(GymMemberStoreDO::getStoreId, store.getId()))
                        == 0) throw exception(COUPON_VERIFICATION_INVALID);
        records.checkPurchase(member.getId());
        return new Verification(row, member, store, card);
    }
}
