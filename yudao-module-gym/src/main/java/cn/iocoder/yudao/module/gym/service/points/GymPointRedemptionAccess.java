package cn.iocoder.yudao.module.gym.service.points;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.points.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.points.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessMutex;
import cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileIdentityService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.Objects;

@Service
public class GymPointRedemptionAccess {
    @Resource private GymPointRedemptionMapper rows;
    @Resource private GymPointRecordMapper ledger;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberMapper members;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymMemberRecordsService records;
    @Resource private GymBusinessMutex mutex;

    public record Redemption(
            GymPointRedemptionDO row,
            GymMemberDO member,
            GymStoreDO store,
            GymCardTemplateDO card) {}

    public static boolean applicable(GymCardTemplateDO card, Long store) {
        if (card == null) return false;
        try {
            return "published".equals(card.getStatus())
                    && ("all".equals(card.getAccessScope())
                            || "selected".equals(card.getAccessScope())
                                    && JsonUtils.parseArray(card.getStoreIds(), Long.class)
                                            .contains(store))
                    && card.getId() != null
                    && card.getFamilyId() != null
                    && card.getVersion() != null
                    && ("count".equals(card.getKind())
                                    && card.getIncludedUses() != null
                                    && card.getIncludedUses() > 0
                            || "period".equals(card.getKind())
                                    && card.getDurationDays() != null
                                    && card.getDurationDays() > 0);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Redemption require(Long id) {
        mutex.lockStoreEntitlements();
        var identity = identities.requireIdentity();
        var row =
                rows.selectOne(
                        new LambdaQueryWrapper<GymPointRedemptionDO>()
                                .eq(GymPointRedemptionDO::getId, id)
                                .last("FOR UPDATE"));
        if (row == null
                || !identity.getUserId().equals(row.getUserId())
                || !identity.getMemberId().equals(row.getMemberId())
                || !"processing".equals(row.getStatus())
                || row.getDebitRecordId() == null) throw exception(POINT_REDEMPTION_INVALID);
        var debit = ledger.selectById(row.getDebitRecordId());
        if (debit == null
                || !row.getMemberId().equals(debit.getMemberId())
                || !"spend".equals(debit.getType())
                || debit.getDelta() != -row.getRequiredPoints()
                || !("redemption:" + id + ":spend").equals(debit.getBusinessKey())
                || ledger.selectCount(GymPointRecordDO::getOriginalRecordId, debit.getId()) != 0)
            throw exception(POINT_REDEMPTION_INVALID);
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
                        == 0) throw exception(POINT_REDEMPTION_INVALID);
        records.checkPurchase(member.getId());
        GymCardTemplateDO card;
        try {
            card = JsonUtils.parseObject(row.getTemplateSnapshot(), GymCardTemplateDO.class);
        } catch (RuntimeException e) {
            throw exception(POINT_REDEMPTION_INVALID);
        }
        if (!applicable(card, store.getId())
                || !Objects.equals(card.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(POINT_REDEMPTION_INVALID);
        return new Redemption(row, member, store, card);
    }
}
