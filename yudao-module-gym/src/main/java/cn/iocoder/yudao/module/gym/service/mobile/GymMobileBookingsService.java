package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordPageReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymBlacklistDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.member.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileBookingProperties;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.course.GymBookingRules;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GymMobileBookingsService {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymBookingRules rules;
    @Resource private GymMobileBookingProperties properties;
    @Resource private GymBookingMapper bookings;
    @Resource private GymCourseSessionMapper sessions;
    @Resource private GymCourseProductMapper products;
    @Resource private GymCoursePackMapper packs;
    @Resource private GymCheckinMapper checkins;
    @Resource private GymLessonRecordMapper lessons;
    @Resource private GymMemberMapper members;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymStoreMapper stores;
    @Resource private GymEmployeeMapper employees;
    @Resource private GymEmployeeStoreMapper employeeStores;
    @Resource private GymBlacklistMapper blacklists;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Booking create(Create req, String key) {
        Long memberId = identities.requireMemberId();
        validate(req);
        return idempotency.execute(
                "booking-create",
                key,
                req,
                Booking.class,
                () -> {
                    var initial = sessions.selectById(req.getSessionId());
                    if (initial == null) throw exception(COURSE_SESSION_NOT_EXISTS);
                    // Same lock order as the admin entry: coach -> member -> session -> booking ->
                    // pack.
                    rules.requireCoach(initial.getCoachId(), initial.getStoreId());
                    var member = members.selectForUpdate(memberId);
                    if (member == null || !"active".equals(member.getStatus()))
                        throw exception(MOBILE_IDENTITY_INVALID);
                    var session = sessions.selectForUpdate(req.getSessionId());
                    if (session == null) throw exception(COURSE_SESSION_NOT_EXISTS);
                    requireActiveStore(session.getStoreId());
                    if (memberships.selectCount(
                                    new LambdaQueryWrapper<GymMemberStoreDO>()
                                            .eq(GymMemberStoreDO::getMemberId, memberId)
                                            .eq(GymMemberStoreDO::getStoreId, session.getStoreId()))
                            == 0) throw exception(BOOKING_INVALID);
                    if (entryBlocked(memberId)) throw exception(MEMBER_ENTRY_BLOCKED);
                    if (!"scheduled".equals(session.getStatus())) throw exception(BOOKING_INVALID);
                    var product = products.selectById(session.getCourseProductId());
                    if (product == null
                            || !"active".equals(product.getStatus())
                            || !session.getCoachId().equals(product.getCoachId())
                            || !session.getStoreId().equals(product.getStoreId()))
                        throw exception(COURSE_PRODUCT_INVALID);
                    var existing =
                            bookings.selectList(
                                    new LambdaQueryWrapper<GymBookingDO>()
                                            .eq(GymBookingDO::getMemberId, memberId)
                                            .eq(GymBookingDO::getSessionId, session.getId())
                                            .last("FOR UPDATE"));
                    var booking = existing.isEmpty() ? new GymBookingDO() : existing.getFirst();
                    if ("confirmed".equals(booking.getStatus())) {
                        if (!Objects.equals(booking.getMemberCoursePackId(), req.getCoursePackId())
                                || booking.getMemberCardId() != null)
                            throw exception(BOOKING_INVALID);
                        return response(booking);
                    }
                    if (booking.getStatus() != null && !"cancelled".equals(booking.getStatus())
                            || !session.getStartsAt().isAfter(clock.now()))
                        throw exception(BOOKING_INVALID);
                    rules.rejectOverlappingBooking(memberId, session);
                    if (session.getReservedCount() >= session.getCapacity())
                        throw exception(BOOKING_CAPACITY_FULL);
                    var pack = packs.selectForUpdate(req.getCoursePackId());
                    // Do not reveal a different member's entitlement details.
                    if (pack == null || !memberId.equals(pack.getMemberId()))
                        throw exception(COURSE_PACK_NOT_EXISTS);
                    rules.validatePack(pack, memberId, session);
                    rules.reservePack(pack);
                    booking.setStoreId(session.getStoreId());
                    booking.setSessionId(session.getId());
                    booking.setMemberId(memberId);
                    booking.setMemberCardId(null);
                    booking.setMemberCoursePackId(pack.getId());
                    booking.setStatus("confirmed");
                    booking.setBookedAt(clock.now().truncatedTo(ChronoUnit.MICROS));
                    booking.setCancelledAt(null);
                    booking.setCancellationReason(null);
                    booking.setCheckinId(null);
                    if (booking.getId() == null) bookings.insert(booking);
                    else bookings.updateById(booking);
                    session.setReservedCount(session.getReservedCount() + 1);
                    sessions.updateById(session);
                    return response(booking);
                });
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Booking cancel(Long id, Cancel req, String key) {
        Long memberId = identities.requireMemberId();
        validate(req);
        return idempotency.execute(
                "booking-cancel:" + id,
                key,
                req,
                Booking.class,
                () -> {
                    var first = owned(id, memberId);
                    var session = sessions.selectForUpdate(first.getSessionId());
                    if (session == null) throw exception(COURSE_SESSION_NOT_EXISTS);
                    var booking = bookings.selectForUpdate(id);
                    if (booking == null || !memberId.equals(booking.getMemberId()))
                        throw exception(BOOKING_NOT_EXISTS);
                    if ("cancelled".equals(booking.getStatus())) return response(booking);
                    if (!"confirmed".equals(booking.getStatus())) throw exception(BOOKING_INVALID);
                    if (!clock.now().isBefore(deadline(session)))
                        throw exception(MOBILE_CANCEL_DEADLINE_PASSED);
                    booking.setCancellationReason(req.getReason());
                    rules.cancel(booking, session);
                    return response(booking);
                });
    }

    public Booking get(Long id) {
        return response(owned(id, identities.requireMemberId()));
    }

    public PageResult<Booking> page(GymMobileRecordPageReqVO req) {
        Long member = identities.requireMemberId();
        validate(req);
        req.validateFilters(Set.of("confirmed", "cancelled", "completed", "no_show"));
        var q =
                new LambdaQueryWrapper<GymBookingDO>()
                        .eq(GymBookingDO::getMemberId, member)
                        .eq(req.getStoreId() != null, GymBookingDO::getStoreId, req.getStoreId())
                        .eq(
                                req.getStatus() != null,
                                GymBookingDO::getStatus,
                                "completed".equals(req.getStatus()) ? "attended" : req.getStatus())
                        .orderByDesc(GymBookingDO::getBookedAt, GymBookingDO::getId);
        if (req.from() != null)
            q.exists(
                    "SELECT 1 FROM gym_course_session s WHERE s.id=gym_booking.session_id "
                            + "AND s.tenant_id={0} AND s.deleted=0 AND s.starts_at >= {1} AND s.starts_at < {2}",
                    TenantContextHolder.getRequiredTenantId(), req.from(), req.to());
        var page = bookings.selectPage(req, q);
        return new PageResult<>(responses(page.getList()), page.getTotal());
    }

    public PageResult<LessonRecord> lessons(GymMobileRecordPageReqVO req) {
        Long member = identities.requireMemberId();
        validate(req);
        req.validateFilters(Set.of());
        var page =
                lessons.selectPage(
                        req,
                        new LambdaQueryWrapper<GymLessonRecordDO>()
                                .eq(GymLessonRecordDO::getMemberId, member)
                                .eq(GymLessonRecordDO::getStatus, "confirmed")
                                .eq(
                                        req.getStoreId() != null,
                                        GymLessonRecordDO::getStoreId,
                                        req.getStoreId())
                                .ge(
                                        req.from() != null,
                                        GymLessonRecordDO::getConfirmedAt,
                                        req.from())
                                .lt(req.to() != null, GymLessonRecordDO::getConfirmedAt, req.to())
                                .orderByDesc(
                                        GymLessonRecordDO::getConfirmedAt,
                                        GymLessonRecordDO::getId));
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                r ->
                                        new LessonRecord(
                                                id(r.getId()),
                                                id(r.getBookingId()),
                                                id(r.getCheckinId()),
                                                time(r.getConfirmedAt()),
                                                r.getMethod(),
                                                r.getSessionUnits(),
                                                r.getRemainingLessonsAfter()))
                        .toList(),
                page.getTotal());
    }

    public PageResult<CourseSession> sessions(Long coach, Sessions req) {
        Long member = identities.requireMemberId();
        validate(req);
        if (!req.getEndDate().isAfter(req.getStartDate())
                || ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate()) > 31)
            throw exception(BUSINESS_DATE_INVALID);
        requireActiveStore(req.getStoreId());
        var employee = employees.selectById(coach);
        var today = clock.now().atZone(GymDateRange.ZONE).toLocalDate();
        if (employee == null
                || !"active".equals(employee.getStatus())
                || employee.getHiredOn().isAfter(today)
                || employeeStores.selectCount(
                                new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                        .eq(GymEmployeeStoreDO::getEmployeeId, coach)
                                        .eq(GymEmployeeStoreDO::getStoreId, req.getStoreId())
                                        .le(GymEmployeeStoreDO::getJoinedOn, today))
                        == 0) throw exception(COURSE_COACH_INVALID);
        var page =
                sessions.selectPage(
                        req,
                        new LambdaQueryWrapper<GymCourseSessionDO>()
                                .eq(GymCourseSessionDO::getCoachId, coach)
                                .eq(GymCourseSessionDO::getStoreId, req.getStoreId())
                                .eq(GymCourseSessionDO::getStatus, "scheduled")
                                .eq(
                                        req.getProductId() != null,
                                        GymCourseSessionDO::getCourseProductId,
                                        req.getProductId())
                                .ge(
                                        GymCourseSessionDO::getStartsAt,
                                        req.getStartDate()
                                                .atStartOfDay(GymDateRange.ZONE)
                                                .toInstant())
                                .lt(
                                        GymCourseSessionDO::getStartsAt,
                                        req.getEndDate()
                                                .atStartOfDay(GymDateRange.ZONE)
                                                .toInstant())
                                .orderByAsc(
                                        GymCourseSessionDO::getStartsAt,
                                        GymCourseSessionDO::getId));
        var availablePacks =
                packs.selectList(
                        new LambdaQueryWrapper<GymCoursePackDO>()
                                .eq(GymCoursePackDO::getMemberId, member)
                                .eq(GymCoursePackDO::getStoreId, req.getStoreId())
                                .eq(GymCoursePackDO::getAssignedCoachId, coach)
                                .eq(GymCoursePackDO::getStatus, "active")
                                .apply("remaining_sessions > reserved_sessions"));
        var existing =
                bookings.selectList(
                        new LambdaQueryWrapper<GymBookingDO>()
                                .eq(GymBookingDO::getMemberId, member)
                                .in(GymBookingDO::getStatus, "confirmed", "attended")
                                .exists(
                                        "SELECT 1 FROM gym_course_session s WHERE s.id=gym_booking.session_id AND s.deleted=0 "
                                                + "AND s.tenant_id={0} AND s.starts_at < {1} AND s.ends_at > {2}",
                                        TenantContextHolder.getRequiredTenantId(),
                                        req.getEndDate()
                                                .atStartOfDay(GymDateRange.ZONE)
                                                .toInstant(),
                                        req.getStartDate()
                                                .atStartOfDay(GymDateRange.ZONE)
                                                .toInstant()));
        List<GymCourseSessionDO> occupied =
                existing.isEmpty()
                        ? List.of()
                        : sessions.selectByIds(
                                existing.stream().map(GymBookingDO::getSessionId).toList());
        Map<Long, GymCourseProductDO> productMap =
                page.getList().isEmpty()
                        ? Map.of()
                        : products
                                .selectByIds(
                                        page.getList().stream()
                                                .map(GymCourseSessionDO::getCourseProductId)
                                                .distinct()
                                                .toList())
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                GymCourseProductDO::getId, Function.identity()));
        boolean blocked = entryBlocked(member);
        boolean memberStore =
                memberships.selectCount(
                                new LambdaQueryWrapper<GymMemberStoreDO>()
                                        .eq(GymMemberStoreDO::getMemberId, member)
                                        .eq(GymMemberStoreDO::getStoreId, req.getStoreId()))
                        > 0;
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                s -> {
                                    var p = productMap.get(s.getCourseProductId());
                                    String reason =
                                            !memberStore
                                                    ? "member_store_required"
                                                    : blocked
                                                            ? "entry_blocked"
                                                            : !s.getStartsAt().isAfter(clock.now())
                                                                    ? "already_started"
                                                                    : s.getReservedCount()
                                                                                    >= s
                                                                                            .getCapacity()
                                                                            ? "capacity_full"
                                                                            : p == null
                                                                                            || !"active"
                                                                                                    .equals(
                                                                                                            p
                                                                                                                    .getStatus())
                                                                                    ? "product_unavailable"
                                                                                    : occupied
                                                                                                    .stream()
                                                                                                    .anyMatch(
                                                                                                            o ->
                                                                                                                    o.getStartsAt()
                                                                                                                                    .isBefore(
                                                                                                                                            s
                                                                                                                                                    .getEndsAt())
                                                                                                                            && o.getEndsAt()
                                                                                                                                    .isAfter(
                                                                                                                                            s
                                                                                                                                                    .getStartsAt()))
                                                                                            ? "time_conflict"
                                                                                            : availablePacks
                                                                                                            .stream()
                                                                                                            .noneMatch(
                                                                                                                    pck ->
                                                                                                                            pck.getCourseProductId()
                                                                                                                                            .equals(
                                                                                                                                                    s
                                                                                                                                                            .getCourseProductId())
                                                                                                                                    && !pck.getStartsAt()
                                                                                                                                            .isAfter(
                                                                                                                                                    s
                                                                                                                                                            .getStartsAt())
                                                                                                                                    && (pck
                                                                                                                                                            .getExpiresAt()
                                                                                                                                                    == null
                                                                                                                                            || pck.getExpiresAt()
                                                                                                                                                    .isAfter(
                                                                                                                                                            s
                                                                                                                                                                    .getEndsAt())))
                                                                                                    ? "course_pack_required"
                                                                                                    : null;
                                    return new CourseSession(
                                            id(s.getId()),
                                            id(s.getStoreId()),
                                            id(s.getCoachId()),
                                            id(s.getCourseProductId()),
                                            time(s.getStartsAt()),
                                            time(s.getEndsAt()),
                                            s.getCapacity(),
                                            Math.max(0, s.getCapacity() - s.getReservedCount()),
                                            reason == null,
                                            reason);
                                })
                        .toList(),
                page.getTotal());
    }

    private boolean entryBlocked(Long member) {
        Instant now = clock.now();
        return blacklists.selectCount(
                        new LambdaQueryWrapper<GymBlacklistDO>()
                                .eq(GymBlacklistDO::getMemberId, member)
                                .isNull(GymBlacklistDO::getRevokedAt)
                                .eq(GymBlacklistDO::getBlockEntry, true)
                                .le(GymBlacklistDO::getStartsAt, now)
                                .and(
                                        w ->
                                                w.isNull(GymBlacklistDO::getEndsAt)
                                                        .or()
                                                        .gt(GymBlacklistDO::getEndsAt, now)))
                > 0;
    }

    private void requireActiveStore(Long id) {
        var store = stores.selectById(id);
        if (store == null || !"active".equals(store.getStatus()))
            throw exception(MEMBER_STORE_INACTIVE);
    }

    private GymBookingDO owned(Long id, Long member) {
        var row =
                bookings.selectOne(
                        new LambdaQueryWrapper<GymBookingDO>()
                                .eq(GymBookingDO::getId, id)
                                .eq(GymBookingDO::getMemberId, member));
        if (row == null) throw exception(BOOKING_NOT_EXISTS);
        return row;
    }

    private Booking response(GymBookingDO row) {
        return responses(List.of(row)).getFirst();
    }

    private List<Booking> responses(List<GymBookingDO> rows) {
        if (rows.isEmpty()) return List.of();
        var sessionMap =
                sessions
                        .selectByIds(
                                rows.stream().map(GymBookingDO::getSessionId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(GymCourseSessionDO::getId, Function.identity()));
        var bookingIds = rows.stream().map(GymBookingDO::getId).toList();
        var checkinRows =
                checkins.selectList(
                        new LambdaQueryWrapper<GymCheckinDO>()
                                .in(GymCheckinDO::getBookingId, bookingIds)
                                .eq(GymCheckinDO::getStatus, "succeeded"));
        var lessonRows =
                lessons.selectList(
                        new LambdaQueryWrapper<GymLessonRecordDO>()
                                .in(GymLessonRecordDO::getBookingId, bookingIds)
                                .eq(GymLessonRecordDO::getStatus, "confirmed"));
        return rows.stream()
                .map(
                        r -> {
                            var session = sessionMap.get(r.getSessionId());
                            boolean checkedIn =
                                    checkinRows.stream()
                                            .anyMatch(
                                                    c ->
                                                            Objects.equals(
                                                                            c.getId(),
                                                                            r.getCheckinId())
                                                                    && Objects.equals(
                                                                            c.getBookingId(),
                                                                            r.getId())
                                                                    && Objects.equals(
                                                                            c.getMemberId(),
                                                                            r.getMemberId())
                                                                    && Objects.equals(
                                                                            c.getStoreId(),
                                                                            r.getStoreId()));
                            var lessonId =
                                    lessonRows.stream()
                                            .filter(
                                                    l ->
                                                            Objects.equals(
                                                                            l.getBookingId(),
                                                                            r.getId())
                                                                    && Objects.equals(
                                                                            l.getMemberId(),
                                                                            r.getMemberId())
                                                                    && Objects.equals(
                                                                            l.getStoreId(),
                                                                            r.getStoreId()))
                                            .map(l -> id(l.getId()))
                                            .findFirst()
                                            .orElse(null);
                            return new Booking(
                                    id(r.getId()),
                                    id(r.getSessionId()),
                                    id(r.getMemberCoursePackId()),
                                    id(r.getStoreId()),
                                    session == null ? null : id(session.getCoachId()),
                                    session == null ? null : time(session.getStartsAt()),
                                    session == null ? null : time(session.getEndsAt()),
                                    "attended".equals(r.getStatus()) ? "completed" : r.getStatus(),
                                    checkedIn,
                                    session == null ? null : time(deadline(session)),
                                    lessonId);
                        })
                .toList();
    }

    private Instant deadline(GymCourseSessionDO session) {
        return session.getStartsAt().minus(properties.getCancelBeforeMinutes(), ChronoUnit.MINUTES);
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    private static String time(Instant value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }
}
