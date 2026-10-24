package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.CARD_NOT_EXISTS;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEntitlementsVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCoursePackDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCoursePackMapper;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.*;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMobileEntitlementsService {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMemberCardMapper cards;
    @Resource private GymMemberCardStoreMapper cardStores;
    @Resource private GymCoursePackMapper packs;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    public PageResult<MemberCard> cards(CardPage req) {
        Long member = identities.requireMemberId();
        validate(req);
        Instant now = clock.now();
        var q = new QueryWrapper<GymMemberCardDO>().eq("member_id", member);
        if (req.getStoreId() != null) {
            // Use current entitlement relationships, not the historical issuance snapshot.
            q.apply(
                    "id IN (SELECT card_id FROM gym_member_card_store WHERE tenant_id={0} "
                            + "AND deleted=0 AND store_id={1})",
                    TenantContextHolder.getRequiredTenantId(), req.getStoreId());
        }
        if (req.getStatus() != null) {
            // Filter effective status before pagination so total matches the visible list.
            q.apply(
                    "(CASE WHEN status <> 'active' THEN status "
                            + "WHEN expires_at IS NOT NULL AND expires_at <= {0} THEN 'expired' "
                            + "WHEN starts_at > {0} THEN 'pending' "
                            + "WHEN kind='count' AND remaining_uses <= 0 THEN 'exhausted' "
                            + "ELSE 'active' END) = {1}",
                    now, req.getStatus());
        }
        q.orderByDesc("create_time", "id");
        var page = cards.selectPage(req, q);
        var stores = storesFor(page.getList());
        return new PageResult<>(
                page.getList().stream()
                        .map(r -> cardResponse(r, stores.getOrDefault(r.getId(), List.of()), now))
                        .toList(),
                page.getTotal());
    }

    public MemberCard card(Long id) {
        Long member = identities.requireMemberId();
        var row =
                cards.selectOne(
                        new LambdaQueryWrapper<GymMemberCardDO>()
                                .eq(GymMemberCardDO::getId, id)
                                .eq(GymMemberCardDO::getMemberId, member));
        if (row == null) throw exception(CARD_NOT_EXISTS);
        return cardResponse(row, storesFor(List.of(row)).getOrDefault(id, List.of()), clock.now());
    }

    public PageResult<CoursePack> packs(PackPage req) {
        Long member = identities.requireMemberId();
        validate(req);
        Instant now = clock.now();
        var page =
                packs.selectPage(
                        req,
                        new LambdaQueryWrapper<GymCoursePackDO>()
                                .eq(GymCoursePackDO::getMemberId, member)
                                .eq(
                                        req.getStoreId() != null,
                                        GymCoursePackDO::getStoreId,
                                        req.getStoreId())
                                .eq(
                                        req.getCoachId() != null,
                                        GymCoursePackDO::getAssignedCoachId,
                                        req.getCoachId())
                                .orderByDesc(
                                        GymCoursePackDO::getCreateTime, GymCoursePackDO::getId));
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                r ->
                                        new CoursePack(
                                                r.getId().toString(),
                                                r.getCourseProductId().toString(),
                                                r.getStoreId().toString(),
                                                r.getAssignedCoachId().toString(),
                                                r.getTotalSessions(),
                                                r.getRemainingSessions(),
                                                r.getReservedSessions(),
                                                Math.max(
                                                        0,
                                                        r.getRemainingSessions()
                                                                - r.getReservedSessions()),
                                                time(r.getExpiresAt()),
                                                status(
                                                        r.getStatus(),
                                                        r.getStartsAt(),
                                                        r.getExpiresAt(),
                                                        r.getRemainingSessions(),
                                                        now)))
                        .toList(),
                page.getTotal());
    }

    private Map<Long, List<String>> storesFor(List<GymMemberCardDO> rows) {
        if (rows.isEmpty()) return Map.of();
        return cardStores
                .selectList(
                        new LambdaQueryWrapper<GymMemberCardStoreDO>()
                                .in(
                                        GymMemberCardStoreDO::getCardId,
                                        rows.stream().map(GymMemberCardDO::getId).toList())
                                .orderByAsc(GymMemberCardStoreDO::getStoreId))
                .stream()
                .collect(
                        Collectors.groupingBy(
                                GymMemberCardStoreDO::getCardId,
                                Collectors.mapping(
                                        r -> r.getStoreId().toString(), Collectors.toList())));
    }

    private MemberCard cardResponse(GymMemberCardDO r, List<String> stores, Instant now) {
        boolean counted = "count".equals(r.getKind());
        return new MemberCard(
                r.getId().toString(),
                r.getName(),
                counted ? "times" : "duration",
                status(
                        r.getStatus(),
                        r.getStartsAt(),
                        r.getExpiresAt(),
                        counted ? r.getRemainingUses() : null,
                        now),
                stores,
                time(r.getStartsAt()),
                time(r.getExpiresAt()),
                counted ? r.getRemainingUses() : null,
                null);
    }

    private static String status(
            String stored, Instant from, Instant until, Integer remaining, Instant now) {
        if (!"active".equals(stored)) return stored;
        if (until != null && !until.isAfter(now)) return "expired";
        if (from != null && from.isAfter(now)) return "pending";
        return remaining != null && remaining <= 0 ? "exhausted" : "active";
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private static String time(Instant value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }
}
