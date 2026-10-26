package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkMembersVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCoursePackDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCoursePackMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService;

import com.baomidou.mybatisplus.core.conditions.query.*;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMobileWorkMemberQueryService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymMemberCoachService scope;
    @Resource private GymMemberMapper members;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymMemberCoachMapper assignments;
    @Resource private GymMemberCardMapper cards;
    @Resource private GymMemberCardStoreMapper cardStores;
    @Resource private GymCoursePackMapper packs;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    // One snapshot keeps assignmentVersion consistent with the returned employee IDs.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<WorkMember> page(MemberPage req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        access.requireAdmin();
        if (req.getStoreId() != null) scope.checkListStore(req.getStoreId());
        Instant now = clock.now();
        var filter = filter(req, now);
        var q =
                new LambdaQueryWrapper<GymMemberDO>()
                        .exists(
                                "SELECT 1 FROM gym_member_store ms WHERE ms.deleted=0 AND ms.member_id=gym_member.id AND "
                                        + filter.sql(),
                                filter.args())
                        .eq(
                                "prospect".equals(req.getSegment()),
                                GymMemberDO::getCustomerStage,
                                "lead");
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String keyword = req.getKeyword().strip();
            q.and(
                    w ->
                            w.like(GymMemberDO::getName, keyword)
                                    .or()
                                    .like(GymMemberDO::getPhone, keyword)
                                    .or()
                                    .like(GymMemberDO::getMemberNo, keyword));
        }
        var page = members.selectPage(req, q.orderByDesc(GymMemberDO::getId));
        if (page.getList().isEmpty()) return new PageResult<>(List.of(), page.getTotal());
        var ids = page.getList().stream().map(GymMemberDO::getId).toList();
        var relations =
                memberships.selectList(
                        new QueryWrapper<GymMemberStoreDO>()
                                .in("member_id", ids)
                                .apply(
                                        filter.sql().replace("ms.", "gym_member_store."),
                                        filter.args())
                                .orderByAsc("store_id"));
        var selected = new LinkedHashMap<Long, GymMemberStoreDO>();
        relations.forEach(r -> selected.putIfAbsent(r.getMemberId(), r));
        return new PageResult<>(responses(page.getList(), selected, now), page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkMember get(Long member, Long store) {
        if (member == null || member <= 0 || store == null || store <= 0)
            throw exception(MOBILE_REQUEST_INVALID);
        access.member(member, store);
        var relation =
                memberships.selectOne(
                        new LambdaQueryWrapper<GymMemberStoreDO>()
                                .eq(GymMemberStoreDO::getMemberId, member)
                                .eq(GymMemberStoreDO::getStoreId, store));
        return responses(List.of(members.selectById(member)), Map.of(member, relation), clock.now())
                .getFirst();
    }

    private record Filter(String sql, Object[] args) {}

    private Filter filter(MemberPage req, Instant now) {
        // Only internal SQL identifiers are concatenated. All client values are bound parameters.
        String sql = "ms.tenant_id={0} AND " + scope.predicate("ms.member_id", "ms.store_id");
        var args = new ArrayList<Object>();
        args.add(TenantContextHolder.getRequiredTenantId());
        if (req.getStoreId() != null) {
            sql += " AND ms.store_id={" + args.size() + "}";
            args.add(req.getStoreId());
        }
        if (req.getCoachId() != null) {
            sql +=
                    " AND EXISTS(SELECT 1 FROM gym_member_coach mc WHERE mc.tenant_id={0} AND mc.deleted=0 AND mc.active=1 AND mc.member_id=ms.member_id AND mc.store_id=ms.store_id AND mc.employee_id={"
                            + args.size()
                            + "})";
            args.add(req.getCoachId());
        }
        String segment = req.getSegment();
        if (Set.of("private_course", "card_expiring", "course_expiring")
                .contains(segment == null ? "all" : segment)) {
            String at = "{" + args.size() + "}";
            args.add(now);
            if ("card_expiring".equals(segment)) {
                sql +=
                        " AND EXISTS(SELECT 1 FROM gym_member_card c WHERE c.tenant_id={0} AND c.deleted=0 AND c.member_id=ms.member_id AND c.status='active' AND (c.starts_at IS NULL OR c.starts_at<="
                                + at
                                + ") AND (c.kind<>'count' OR c.remaining_uses>0) AND c.expires_at>"
                                + at
                                + " AND c.expires_at<={"
                                + args.size()
                                + "} AND EXISTS(SELECT 1 FROM gym_member_card_store cs WHERE cs.tenant_id={0} AND cs.deleted=0 AND cs.card_id=c.id AND cs.store_id=ms.store_id))";
                args.add(
                        now.plus(
                                req.getExpireWithinDays() == null ? 30 : req.getExpireWithinDays(),
                                ChronoUnit.DAYS));
            } else {
                sql +=
                        " AND EXISTS(SELECT 1 FROM gym_course_pack p WHERE p.tenant_id={0} AND p.deleted=0 AND p.member_id=ms.member_id AND p.store_id=ms.store_id AND p.status='active' AND p.remaining_sessions>0 AND (p.starts_at IS NULL OR p.starts_at<="
                                + at
                                + ")";
                if ("private_course".equals(segment))
                    sql += " AND (p.expires_at IS NULL OR p.expires_at>" + at + "))";
                else {
                    sql += " AND p.expires_at>" + at + " AND p.expires_at<={" + args.size() + "})";
                    args.add(
                            now.plus(
                                    req.getExpireWithinDays() == null
                                            ? 30
                                            : req.getExpireWithinDays(),
                                    ChronoUnit.DAYS));
                }
            }
        }
        return new Filter(sql, args.toArray());
    }

    private List<WorkMember> responses(
            List<GymMemberDO> rows, Map<Long, GymMemberStoreDO> selected, Instant now) {
        var ids = rows.stream().map(GymMemberDO::getId).toList();
        // Batch all projections; authorization remains correlated to each member/store pair.
        var coachRows =
                assignments.selectList(
                        new LambdaQueryWrapper<GymMemberCoachDO>()
                                .in(GymMemberCoachDO::getMemberId, ids)
                                .eq(GymMemberCoachDO::getActive, true)
                                .apply(
                                        scope.predicate(
                                                "gym_member_coach.member_id",
                                                "gym_member_coach.store_id"))
                                .orderByAsc(GymMemberCoachDO::getEmployeeId));
        var cardRows =
                cards.selectList(
                        new LambdaQueryWrapper<GymMemberCardDO>()
                                .in(GymMemberCardDO::getMemberId, ids)
                                .eq(GymMemberCardDO::getStatus, "active")
                                .exists(
                                        "SELECT 1 FROM gym_member_card_store cs WHERE cs.tenant_id={0} AND cs.deleted=0 AND cs.card_id=gym_member_card.id AND "
                                                + scope.predicate(
                                                        "gym_member_card.member_id", "cs.store_id"),
                                        TenantContextHolder.getRequiredTenantId()));
        Map<Long, Set<Long>> cardStoreIds =
                cardRows.isEmpty()
                        ? Map.of()
                        : cardStores
                                .selectList(
                                        new LambdaQueryWrapper<GymMemberCardStoreDO>()
                                                .in(
                                                        GymMemberCardStoreDO::getCardId,
                                                        cardRows.stream()
                                                                .map(GymMemberCardDO::getId)
                                                                .toList()))
                                .stream()
                                .collect(
                                        Collectors.groupingBy(
                                                GymMemberCardStoreDO::getCardId,
                                                Collectors.mapping(
                                                        GymMemberCardStoreDO::getStoreId,
                                                        Collectors.toSet())));
        var packRows =
                packs.selectList(
                        new LambdaQueryWrapper<GymCoursePackDO>()
                                .in(GymCoursePackDO::getMemberId, ids)
                                .eq(GymCoursePackDO::getStatus, "active")
                                .apply(
                                        scope.predicate(
                                                "gym_course_pack.member_id",
                                                "gym_course_pack.store_id")));
        return rows.stream()
                .map(
                        row -> {
                            var relation = selected.get(row.getId());
                            var store = relation.getStoreId();
                            var visibleCards =
                                    cardRows.stream()
                                            .filter(
                                                    c ->
                                                            c.getMemberId().equals(row.getId())
                                                                    && cardStoreIds
                                                                            .getOrDefault(
                                                                                    c.getId(),
                                                                                    Set.of())
                                                                            .contains(store)
                                                                    && current(
                                                                            c.getStartsAt(),
                                                                            c.getExpiresAt(),
                                                                            now)
                                                                    && (!"count".equals(c.getKind())
                                                                            || c.getRemainingUses()
                                                                                    > 0))
                                            .toList();
                            var counted =
                                    visibleCards.stream()
                                            .filter(c -> "count".equals(c.getKind()))
                                            .toList();
                            var visiblePacks =
                                    packRows.stream()
                                            .filter(
                                                    p ->
                                                            p.getMemberId().equals(row.getId())
                                                                    && p.getStoreId().equals(store)
                                                                    && p.getRemainingSessions() > 0
                                                                    && current(
                                                                            p.getStartsAt(),
                                                                            p.getExpiresAt(),
                                                                            now))
                                            .toList();
                            return new WorkMember(
                                    row.getId().toString(),
                                    row.getName(),
                                    mask(row.getPhone()),
                                    "lead".equals(row.getCustomerStage())
                                            ? "prospect"
                                            : row.getCustomerStage(),
                                    store.toString(),
                                    coachRows.stream()
                                            .filter(
                                                    c ->
                                                            c.getMemberId().equals(row.getId())
                                                                    && c.getStoreId().equals(store))
                                            .map(c -> c.getEmployeeId().toString())
                                            .distinct()
                                            .toList(),
                                    new CardSummary(
                                            visibleCards.size(),
                                            visibleCards.size() - counted.size(),
                                            counted.size(),
                                            counted.stream()
                                                    .mapToLong(GymMemberCardDO::getRemainingUses)
                                                    .sum(),
                                            counted.stream()
                                                    .mapToLong(GymMemberCardDO::getReservedUses)
                                                    .sum(),
                                            counted.stream()
                                                    .mapToLong(
                                                            c ->
                                                                    Math.max(
                                                                            0,
                                                                            (long)
                                                                                            c
                                                                                                    .getRemainingUses()
                                                                                    - c
                                                                                            .getReservedUses()))
                                                    .sum(),
                                            nearest(
                                                    visibleCards.stream()
                                                            .map(GymMemberCardDO::getExpiresAt)
                                                            .toList())),
                                    new CourseSummary(
                                            visiblePacks.size(),
                                            visiblePacks.stream()
                                                    .mapToLong(
                                                            GymCoursePackDO::getRemainingSessions)
                                                    .sum(),
                                            visiblePacks.stream()
                                                    .mapToLong(GymCoursePackDO::getReservedSessions)
                                                    .sum(),
                                            visiblePacks.stream()
                                                    .mapToLong(
                                                            p ->
                                                                    Math.max(
                                                                            0,
                                                                            (long)
                                                                                            p
                                                                                                    .getRemainingSessions()
                                                                                    - p
                                                                                            .getReservedSessions()))
                                                    .sum(),
                                            nearest(
                                                    visiblePacks.stream()
                                                            .map(GymCoursePackDO::getExpiresAt)
                                                            .toList())),
                                    relation.getAssignmentVersion());
                        })
                .toList();
    }

    private static boolean current(Instant start, Instant end, Instant now) {
        return (start == null || !start.isAfter(now)) && (end == null || end.isAfter(now));
    }

    private static String nearest(List<Instant> ends) {
        return ends.stream()
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .map(t -> t.atZone(GymDateRange.ZONE).toOffsetDateTime().toString())
                .orElse(null);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() <= 7) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
