package cn.iocoder.yudao.module.gym.service.groupbuy;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymVerificationSettingsReqVO;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponPreviewVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.groupbuy.GymCouponPreviewDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymBlacklistDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSalesSettingDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.groupbuy.GymCouponPreviewMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymBlacklistMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSalesSettingMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses.Prepare;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileCouponProperties;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileIdentityService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymCouponPreviewTransactions {
    @Resource private GymMobileCouponProperties properties;
    @Resource private GymCouponCipher cipher;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymBlacklistMapper blacklist;
    @Resource private GymSalesSettingMapper settings;
    @Resource private GymCardTemplateMapper templates;
    @Resource private GymCouponPreviewMapper previews;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    public record Context(
            Long tenant,
            Long user,
            Long member,
            String mobile,
            Long settingsRevision,
            String mappingSnapshot) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Context begin(Request req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        var identity = identities.requireIdentity();
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (!properties.isEnabled() || properties.store(tenant, req.getStoreId()).isEmpty())
            throw exception(COUPON_NOT_CONFIGURED);
        cipher.requireConfigured();
        var store = stores.selectById(req.getStoreId());
        if (store == null
                || !"active".equals(store.getStatus())
                || memberships.selectCount(
                                new LambdaQueryWrapper<GymMemberStoreDO>()
                                        .eq(GymMemberStoreDO::getMemberId, identity.getMemberId())
                                        .eq(GymMemberStoreDO::getStoreId, store.getId()))
                        == 0) throw exception(COUPON_PREVIEW_INVALID);
        var now = clock.now();
        if (blacklist.selectCount(
                        new LambdaQueryWrapper<GymBlacklistDO>()
                                .eq(GymBlacklistDO::getMemberId, identity.getMemberId())
                                .eq(GymBlacklistDO::getBlockPurchase, true)
                                .isNull(GymBlacklistDO::getRevokedAt)
                                .le(GymBlacklistDO::getStartsAt, now)
                                .and(
                                        w ->
                                                w.isNull(GymBlacklistDO::getEndsAt)
                                                        .or()
                                                        .gt(GymBlacklistDO::getEndsAt, now)))
                > 0) throw exception(COUPON_PREVIEW_INVALID);
        var setting =
                settings.selectOne(
                        new LambdaQueryWrapper<GymSalesSettingDO>()
                                .eq(GymSalesSettingDO::getStoreId, store.getId())
                                .eq(GymSalesSettingDO::getKind, "verification-settings"));
        GymVerificationSettingsReqVO config;
        try {
            config =
                    setting == null
                            ? null
                            : JsonUtils.parseObject(
                                    setting.getContent(), GymVerificationSettingsReqVO.class);
            if (config == null
                    || !validator.validate(config).isEmpty()
                    || !Boolean.TRUE.equals(config.getEnabled())
                    || !config.getSupportedChannels().contains(req.getPlatform())
                    || Boolean.TRUE.equals(config.getRequireCheckin()))
                throw exception(COUPON_NOT_CONFIGURED);
            if ((config.getAllowedStart() == null) != (config.getAllowedEnd() == null))
                throw exception(COUPON_NOT_CONFIGURED);
            if (config.getAllowedStart() != null) {
                var start = LocalTime.parse(config.getAllowedStart());
                var end = LocalTime.parse(config.getAllowedEnd());
                var time = now.atZone(GymDateRange.ZONE).toLocalTime();
                if (!end.isAfter(start) || time.isBefore(start) || !time.isBefore(end))
                    throw exception(COUPON_NOT_CONFIGURED);
            }
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw exception(COUPON_NOT_CONFIGURED);
        }
        return new Context(
                tenant,
                identity.getUserId(),
                identity.getMemberId(),
                identities.requireUser().getMobile(),
                setting.getRevision(),
                JsonUtils.toJsonString(new TreeMap<>(properties.store(tenant, store.getId()))));
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public Preview save(Request req, Context before, Prepare prepared) {
        // Network access has finished; recheck identity, scope, settings and mapping before saving.
        var current = begin(req);
        if (!current.equals(before)) throw exception(COUPON_PREVIEW_INVALID);
        var now = clock.now().truncatedTo(ChronoUnit.MICROS);
        String reason = null;
        if (!Integer.valueOf(0).equals(prepared.getBizType())
                || !Boolean.FALSE.equals(prepared.getTgTimesCardFlag())
                || !Integer.valueOf(0).equals(prepared.getDealType())
                || !Integer.valueOf(1).equals(prepared.getCount())
                || prepared.getReceiptInfoMap() != null
                        && !prepared.getReceiptInfoMap().isBlank()
                        && !"{}".equals(prepared.getReceiptInfoMap())
                || prepared.getRelatedReceiptInfo() != null
                        && !prepared.getRelatedReceiptInfo().isEmpty()
                || prepared.getDealQuantity() != null && prepared.getDealQuantity() != 1)
            reason = "当前券型或数量暂不支持自助兑换，请联系门店";
        else if (!Integer.valueOf(1).equals(prepared.getVerificationType()))
            reason = "团购券当前不在可核销时段";
        Instant vendorExpiry = null;
        if (prepared.getReceiptEndDate() != null) {
            try {
                vendorExpiry = Instant.ofEpochMilli(prepared.getReceiptEndDate());
            } catch (RuntimeException ignored) {
            }
        }
        if (reason == null && (vendorExpiry == null || !vendorExpiry.isAfter(now)))
            reason = "团购券已过期或有效期无法确认";
        var mapping = properties.store(before.tenant(), req.getStoreId()).get(prepared.getDealId());
        GymCardTemplateDO card = null;
        if (mapping != null
                && mapping.isEnabled()
                && Objects.equals(mapping.getDealGroupId(), prepared.getDealGroupId())
                && mapping.getCardTemplateId() != null
                && mapping.getCardTemplateId() > 0)
            card = templates.selectById(mapping.getCardTemplateId());
        boolean applicable = false;
        try {
            applicable =
                    card != null
                            && "published".equals(card.getStatus())
                            && card.getVersion() != null
                            && card.getFamilyId() != null
                            && ("all".equals(card.getAccessScope())
                                    || "selected".equals(card.getAccessScope())
                                            && JsonUtils.parseArray(card.getStoreIds(), Long.class)
                                                    .contains(req.getStoreId()))
                            && ("count".equals(card.getKind())
                                            && card.getIncludedUses() != null
                                            && card.getIncludedUses() > 0
                                    || "period".equals(card.getKind())
                                            && card.getDurationDays() != null
                                            && card.getDurationDays() > 0);
        } catch (RuntimeException ignored) {
        }
        if (reason == null && !applicable) reason = "团购商品尚未配置可用会员卡，请联系门店";
        if (prepared.getDealTitle() == null
                || prepared.getDealTitle().isBlank()
                || prepared.getDealTitle().length() > 200) throw exception(COUPON_PROVIDER_INVALID);
        var expiry = now.plusSeconds(300);
        if (reason == null && vendorExpiry.isBefore(expiry)) expiry = vendorExpiry;
        var row = new GymCouponPreviewDO();
        row.setUserId(before.user());
        row.setMemberId(before.member());
        row.setStoreId(req.getStoreId());
        row.setPlatform(req.getPlatform());
        row.setCouponHash(cipher.hash(before.tenant(), req.getPlatform(), req.getCouponCode()));
        row.setCouponCiphertext(
                cipher.seal(req.getCouponCode(), before.tenant(), req.getStoreId(), before.user()));
        row.setDealId(prepared.getDealId());
        row.setDealGroupId(prepared.getDealGroupId());
        row.setSettingsRevision(before.settingsRevision());
        row.setMappingFingerprint(cn.hutool.crypto.SecureUtil.sha256(before.mappingSnapshot()));
        row.setCardTemplateId(applicable ? card.getId() : null);
        row.setTemplateSnapshot(applicable ? JsonUtils.toJsonString(card) : null);
        row.setProductName(prepared.getDealTitle());
        row.setRedeemable(reason == null);
        row.setReason(reason);
        row.setIssuedAt(now);
        row.setExpiresAt(expiry);
        previews.insert(row);
        return new Preview(
                row.getId().toString(),
                row.getProductName(),
                row.getRedeemable(),
                reason,
                expiry.atZone(GymDateRange.ZONE).toOffsetDateTime().toString());
    }
}
