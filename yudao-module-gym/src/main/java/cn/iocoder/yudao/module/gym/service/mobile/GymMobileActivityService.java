package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.marketing.vo.GymActivitySaveReqVO;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileActivityVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.marketing.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.marketing.*;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.marketing.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymMobileActivityService {
    @Resource private GymActivityMapper activities;
    @Resource private GymActivityStoreMapper links;
    @Resource private GymActivityRegistrationMapper registrations;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberMapper members;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileWorkAccess access;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessClock clock;
    @Resource private GymMarketingService marketing;
    @Resource private GymActivityParticipationService participation;
    @Resource private GymMobilePublicService content;
    @Resource private Validator validator;

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private void positive(Long id) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
    }

    private GymActivityDO row(Long id, boolean lock) {
        positive(id);
        var q = new LambdaQueryWrapper<GymActivityDO>().eq(GymActivityDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = activities.selectOne(q);
        if (row == null) throw exception(ACTIVITY_NOT_EXISTS);
        return row;
    }

    private List<Long> storeIds(Long id, boolean active) {
        var q =
                new LambdaQueryWrapper<GymActivityStoreDO>()
                        .eq(GymActivityStoreDO::getActivityId, id);
        if (active)
            q.exists(
                    "SELECT 1 FROM gym_store s WHERE s.id=gym_activity_store.store_id AND s.tenant_id={0} AND s.deleted=0 AND s.status='active'",
                    TenantContextHolder.getRequiredTenantId());
        return links.selectList(q.orderByAsc(GymActivityStoreDO::getStoreId)).stream()
                .map(GymActivityStoreDO::getStoreId)
                .toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Activity> page(ActivityPage req) {
        TenantContextHolder.getRequiredTenantId();
        validate(req);
        var store = stores.selectById(req.getStoreId());
        if (store == null || !"active".equals(store.getStatus())) throw exception(STORE_NOT_EXISTS);
        Instant now = now();
        var q =
                new LambdaQueryWrapper<GymActivityDO>()
                        .in(GymActivityDO::getStatus, List.of("published", "closed"))
                        .exists(
                                "SELECT 1 FROM gym_activity_store ast WHERE ast.activity_id=gym_activity.id AND ast.tenant_id={0} AND ast.deleted=0 AND ast.store_id={1}",
                                TenantContextHolder.getRequiredTenantId(), req.getStoreId());
        if ("open".equals(req.getStatus()))
            q.eq(GymActivityDO::getStatus, "published")
                    .le(GymActivityDO::getRegistrationStartsAt, now)
                    .gt(GymActivityDO::getRegistrationEndsAt, now);
        if ("ended".equals(req.getStatus()))
            q.and(
                    w ->
                            w.eq(GymActivityDO::getStatus, "closed")
                                    .or()
                                    .le(GymActivityDO::getEndsAt, now));
        var page =
                activities.selectPage(
                        req,
                        q.orderByDesc(GymActivityDO::getStartsAt)
                                .orderByDesc(GymActivityDO::getId));
        return new PageResult<>(
                page.getList().stream().map(r -> project(r, req.getStoreId(), true, now)).toList(),
                page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Activity get(Long id) {
        TenantContextHolder.getRequiredTenantId();
        var row = row(id, false);
        var ids = storeIds(id, true);
        if (!Set.of("published", "closed").contains(row.getStatus()) || ids.isEmpty())
            throw exception(ACTIVITY_NOT_EXISTS);
        return project(row, ids.getFirst(), true, now());
    }

    private String time(Instant value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }

    private Activity project(GymActivityDO row, Long store, boolean publicView, Instant now) {
        var ids = storeIds(row.getId(), publicView);
        long count =
                registrations.selectCount(
                        new LambdaQueryWrapper<GymActivityRegistrationDO>()
                                .eq(GymActivityRegistrationDO::getActivityId, row.getId())
                                .eq(GymActivityRegistrationDO::getStatus, "confirmed"));
        String status;
        if ("cancelled".equals(row.getStatus())) status = "cancelled";
        else if ("closed".equals(row.getStatus()) || !now.isBefore(row.getEndsAt()))
            status = "ended";
        else if (now.isBefore(row.getRegistrationStartsAt())) status = "upcoming";
        else if (now.isBefore(row.getRegistrationEndsAt())) status = "open";
        else if (now.isBefore(row.getStartsAt())) status = "registration_closed";
        else status = "ongoing";
        return new Activity(
                row.getId().toString(),
                store.toString(),
                ids.stream().map(String::valueOf).toList(),
                row.getName(),
                row.getDescription(),
                content.activityMedia(row),
                time(row.getStartsAt()),
                time(row.getEndsAt()),
                time(row.getRegistrationEndsAt()),
                time(row.getCancelDeadline()),
                row.getCapacity(),
                count,
                status);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Activity create(Create req, String key) {
        access.requireAdmin();
        validate(req);
        scope.check(req.getStoreId());
        return idempotency.execute(
                "work-activity-create",
                key,
                req,
                Activity.class,
                () -> {
                    var now = now();
                    if (!req.getStartsAt().isAfter(now)
                            || !req.getRegistrationDeadline().isAfter(now))
                        throw exception(ACTIVITY_INVALID);
                    var old = new GymActivitySaveReqVO();
                    old.setStoreIds(List.of(req.getStoreId()));
                    old.setName(req.getTitle().strip());
                    old.setDescription(req.getDescription());
                    old.setCoverFileId(req.getCoverFileId());
                    old.setStartsAt(req.getStartsAt());
                    old.setEndsAt(req.getEndsAt());
                    old.setRegistrationStartsAt(now);
                    old.setRegistrationEndsAt(req.getRegistrationDeadline());
                    old.setCancelDeadline(req.getCancelDeadline());
                    old.setCapacity(req.getCapacity());
                    Long id = Long.valueOf(marketing.createActivity(old).getId());
                    var publish = new GymActivitySaveReqVO();
                    publish.setStatus("published");
                    marketing.patchActivity(id, publish);
                    return get(id);
                });
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Registration register(Long id, String key) {
        var identity = identities.requireIdentity();
        positive(id);
        Long member = identity.getMemberId();
        // Map.of iteration order can differ between JVMs. Two legacy keys have only two orders.
        var payload = new LinkedHashMap<String, Long>();
        payload.put("activityId", id);
        payload.put("memberId", member);
        var legacyPayload = new LinkedHashMap<String, Long>();
        legacyPayload.put("memberId", member);
        legacyPayload.put("activityId", id);
        return idempotency.executeWithLegacyPayload(
                "activity-register",
                key,
                payload,
                legacyPayload,
                Registration.class,
                () -> {
                    var row = row(id, true);
                    var m = members.selectForUpdate(member);
                    if (m == null || !"active".equals(m.getStatus()))
                        throw exception(MOBILE_IDENTITY_INVALID);
                    var existing =
                            registrations.selectOne(
                                    new LambdaQueryWrapper<GymActivityRegistrationDO>()
                                            .eq(GymActivityRegistrationDO::getActivityId, id)
                                            .eq(GymActivityRegistrationDO::getMemberId, member));
                    var valid = storeIds(id, true);
                    var memberStores =
                            memberships
                                    .selectList(
                                            new LambdaQueryWrapper<GymMemberStoreDO>()
                                                    .eq(GymMemberStoreDO::getMemberId, member))
                                    .stream()
                                    .map(GymMemberStoreDO::getStoreId)
                                    .toList();
                    valid = valid.stream().filter(memberStores::contains).toList();
                    Long selected =
                            existing != null
                                    ? existing.getStoreId()
                                    : valid.contains(identity.getDefaultStoreId())
                                            ? identity.getDefaultStoreId()
                                            : valid.isEmpty() ? null : valid.getFirst();
                    if (selected == null || !valid.contains(selected))
                        throw exception(ACTIVITY_REGISTRATION_INVALID);
                    var result = participation.register(row, m, stores.selectById(selected));
                    return new Registration(
                            result.getId().toString(),
                            id.toString(),
                            "registered",
                            time(result.getRegisteredAt()));
                });
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Cancelled cancel(Long id) {
        Long member = identities.requireMemberId();
        var row = row(id, true);
        var registration =
                registrations.selectOne(
                        new LambdaQueryWrapper<GymActivityRegistrationDO>()
                                .eq(GymActivityRegistrationDO::getActivityId, id)
                                .eq(GymActivityRegistrationDO::getMemberId, member));
        if (registration == null || "cancelled".equals(registration.getStatus()))
            return new Cancelled("cancelled");
        var deadline =
                row.getCancelDeadline() == null ? row.getStartsAt() : row.getCancelDeadline();
        if (!"published".equals(row.getStatus()) || !now().isBefore(deadline))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        registration.setStatus("cancelled");
        registration.setCancelledAt(now());
        registrations.updateById(registration);
        return new Cancelled("cancelled");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<History> history(RegistrationPage req) {
        Long member = identities.requireMemberId();
        validate(req);
        var q =
                new LambdaQueryWrapper<GymActivityRegistrationDO>()
                        .eq(GymActivityRegistrationDO::getMemberId, member)
                        .eq(
                                req.getStatus() != null,
                                GymActivityRegistrationDO::getStatus,
                                "registered".equals(req.getStatus())
                                        ? "confirmed"
                                        : req.getStatus())
                        .orderByDesc(GymActivityRegistrationDO::getRegisteredAt)
                        .orderByDesc(GymActivityRegistrationDO::getId);
        var page = registrations.selectPage(req, q);
        var now = now();
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                r -> {
                                    var a = activities.selectById(r.getActivityId());
                                    return new History(
                                            r.getId().toString(),
                                            a == null
                                                    ? null
                                                    : project(a, r.getStoreId(), false, now),
                                            "confirmed".equals(r.getStatus())
                                                    ? "registered"
                                                    : r.getStatus(),
                                            time(r.getRegisteredAt()));
                                })
                        .toList(),
                page.getTotal());
    }
}
