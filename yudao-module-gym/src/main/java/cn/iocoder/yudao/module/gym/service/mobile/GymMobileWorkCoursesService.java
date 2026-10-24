package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkCoursesVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileBookingProperties;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;
import cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class GymMobileWorkCoursesService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymMemberCoachService scope;
    @Resource private GymBookingMapper bookings;
    @Resource private GymCourseSessionMapper sessions;
    @Resource private GymCheckinMapper checkins;
    @Resource private GymLessonRecordMapper lessons;
    @Resource private GymMemberMapper members;
    @Resource private GymMobileBookingProperties properties;
    @Resource private Validator validator;

    public PageResult<WorkBooking> bookings(BookingPage req) {
        validate(req);
        var q =
                bookingScope()
                        .eq(req.getStoreId() != null, GymBookingDO::getStoreId, req.getStoreId())
                        .eq(
                                req.getStatus() != null,
                                GymBookingDO::getStatus,
                                "completed".equals(req.getStatus()) ? "attended" : req.getStatus())
                        .exists(
                                sessionRelation() + " AND s.starts_at >= {1} AND s.starts_at < {2}",
                                TenantContextHolder.getRequiredTenantId(),
                                req.from(),
                                req.to())
                        .orderByDesc(GymBookingDO::getBookedAt, GymBookingDO::getId);
        if (req.getCoachId() != null)
            q.exists(
                    sessionRelation() + " AND s.coach_id={1}",
                    TenantContextHolder.getRequiredTenantId(),
                    req.getCoachId());
        var page = bookings.selectPage(req, q);
        return new PageResult<>(bookingResponses(page.getList()), page.getTotal());
    }

    public WorkBooking booking(Long id) {
        access.requireAdmin();
        var row = bookings.selectOne(bookingScope().eq(GymBookingDO::getId, id));
        if (row == null) throw exception(BOOKING_NOT_EXISTS);
        return bookingResponses(List.of(row)).getFirst();
    }

    private LambdaQueryWrapper<GymBookingDO> bookingScope() {
        return new LambdaQueryWrapper<GymBookingDO>()
                .apply(scope.predicate("gym_booking.member_id", "gym_booking.store_id"))
                .exists(
                        sessionRelation()
                                + " AND "
                                + scope.coachPredicate("s.coach_id", "s.store_id"),
                        TenantContextHolder.getRequiredTenantId());
    }

    private String sessionRelation() {
        return "SELECT 1 FROM gym_course_session s WHERE s.id=gym_booking.session_id AND s.store_id=gym_booking.store_id AND s.tenant_id={0} AND s.deleted=0";
    }

    public PageResult<WorkCheckin> checkins(CheckinPage req) {
        validate(req);
        var page =
                checkins.selectPage(
                        req,
                        new LambdaQueryWrapper<GymCheckinDO>()
                                .apply(
                                        scope.predicate(
                                                "gym_checkin.member_id", "gym_checkin.store_id"))
                                .eq(GymCheckinDO::getStatus, "succeeded")
                                .eq(
                                        req.getStoreId() != null,
                                        GymCheckinDO::getStoreId,
                                        req.getStoreId())
                                .eq(
                                        req.getMemberId() != null,
                                        GymCheckinDO::getMemberId,
                                        req.getMemberId())
                                .ge(GymCheckinDO::getEnteredAt, req.from())
                                .lt(GymCheckinDO::getEnteredAt, req.to())
                                .orderByDesc(GymCheckinDO::getEnteredAt, GymCheckinDO::getId));
        if (page.getList().isEmpty()) return new PageResult<>(List.of(), page.getTotal());
        var names = names(page.getList().stream().map(GymCheckinDO::getMemberId).toList());
        var bookingIds =
                page.getList().stream()
                        .map(GymCheckinDO::getBookingId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<Long, GymBookingDO> bookingMap =
                bookingIds.isEmpty()
                        ? Map.of()
                        : bookings.selectByIds(bookingIds).stream()
                                .collect(
                                        Collectors.toMap(GymBookingDO::getId, Function.identity()));
        var sessionMap = sessionMap(bookingMap.values());
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                row -> {
                                    var booking = bookingMap.get(row.getBookingId());
                                    boolean linked =
                                            booking != null
                                                    && Objects.equals(
                                                            row.getMemberId(),
                                                            booking.getMemberId())
                                                    && Objects.equals(
                                                            row.getStoreId(), booking.getStoreId());
                                    var session =
                                            linked ? sessionMap.get(booking.getSessionId()) : null;
                                    var coach =
                                            session != null
                                                            && Objects.equals(
                                                                    session.getStoreId(),
                                                                    row.getStoreId())
                                                    ? id(session.getCoachId())
                                                    : null;
                                    return new WorkCheckin(
                                            id(row.getId()),
                                            id(row.getStoreId()),
                                            time(row.getEnteredAt()),
                                            time(row.getExitedAt()),
                                            row.getMethod(),
                                            linked ? id(booking.getId()) : null,
                                            id(row.getMemberId()),
                                            names.get(row.getMemberId()),
                                            coach);
                                })
                        .toList(),
                page.getTotal());
    }

    public PageResult<WorkLessonRecord> lessons(LessonPage req) {
        validate(req);
        var page =
                lessons.selectPage(
                        req,
                        new LambdaQueryWrapper<GymLessonRecordDO>()
                                .apply(
                                        scope.predicate(
                                                "gym_lesson_record.member_id",
                                                "gym_lesson_record.store_id"))
                                .apply(
                                        scope.coachPredicate(
                                                "gym_lesson_record.coach_id",
                                                "gym_lesson_record.store_id"))
                                .eq(GymLessonRecordDO::getStatus, "confirmed")
                                .eq(
                                        req.getStoreId() != null,
                                        GymLessonRecordDO::getStoreId,
                                        req.getStoreId())
                                .eq(
                                        req.getCoachId() != null,
                                        GymLessonRecordDO::getCoachId,
                                        req.getCoachId())
                                .ge(GymLessonRecordDO::getConfirmedAt, req.from())
                                .lt(GymLessonRecordDO::getConfirmedAt, req.to())
                                .orderByDesc(
                                        GymLessonRecordDO::getConfirmedAt,
                                        GymLessonRecordDO::getId));
        var names = names(page.getList().stream().map(GymLessonRecordDO::getMemberId).toList());
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                row ->
                                        new WorkLessonRecord(
                                                id(row.getId()),
                                                id(row.getBookingId()),
                                                id(row.getCheckinId()),
                                                time(row.getConfirmedAt()),
                                                row.getMethod(),
                                                row.getSessionUnits(),
                                                row.getRemainingLessonsAfter(),
                                                id(row.getMemberId()),
                                                names.get(row.getMemberId()),
                                                id(row.getCoachId()),
                                                id(row.getStoreId())))
                        .toList(),
                page.getTotal());
    }

    private List<WorkBooking> bookingResponses(List<GymBookingDO> rows) {
        if (rows.isEmpty()) return List.of();
        var sessionMap = sessionMap(rows);
        var names = names(rows.stream().map(GymBookingDO::getMemberId).toList());
        var ids = rows.stream().map(GymBookingDO::getId).toList();
        var checkinRows =
                checkins.selectList(
                        new LambdaQueryWrapper<GymCheckinDO>()
                                .in(GymCheckinDO::getBookingId, ids)
                                .eq(GymCheckinDO::getStatus, "succeeded"));
        var lessonRows =
                lessons.selectList(
                        new LambdaQueryWrapper<GymLessonRecordDO>()
                                .in(GymLessonRecordDO::getBookingId, ids)
                                .eq(GymLessonRecordDO::getStatus, "confirmed"));
        return rows.stream()
                .map(
                        row -> {
                            var session = sessionMap.get(row.getSessionId());
                            boolean checkedIn =
                                    checkinRows.stream()
                                            .anyMatch(
                                                    c ->
                                                            Objects.equals(
                                                                            c.getId(),
                                                                            row.getCheckinId())
                                                                    && Objects.equals(
                                                                            c.getBookingId(),
                                                                            row.getId())
                                                                    && Objects.equals(
                                                                            c.getMemberId(),
                                                                            row.getMemberId())
                                                                    && Objects.equals(
                                                                            c.getStoreId(),
                                                                            row.getStoreId()));
                            String lessonId =
                                    lessonRows.stream()
                                            .filter(
                                                    l ->
                                                            Objects.equals(
                                                                            l.getBookingId(),
                                                                            row.getId())
                                                                    && Objects.equals(
                                                                            l.getMemberId(),
                                                                            row.getMemberId())
                                                                    && Objects.equals(
                                                                            l.getStoreId(),
                                                                            row.getStoreId())
                                                                    && Objects.equals(
                                                                            l.getCoachId(),
                                                                            session.getCoachId()))
                                            .map(l -> id(l.getId()))
                                            .findFirst()
                                            .orElse(null);
                            return new WorkBooking(
                                    id(row.getId()),
                                    id(row.getSessionId()),
                                    id(row.getMemberCoursePackId()),
                                    id(row.getStoreId()),
                                    id(session.getCoachId()),
                                    time(session.getStartsAt()),
                                    time(session.getEndsAt()),
                                    "attended".equals(row.getStatus())
                                            ? "completed"
                                            : row.getStatus(),
                                    checkedIn,
                                    time(
                                            session.getStartsAt()
                                                    .minus(
                                                            properties.getCancelBeforeMinutes(),
                                                            ChronoUnit.MINUTES)),
                                    lessonId,
                                    id(row.getMemberId()),
                                    names.get(row.getMemberId()));
                        })
                .toList();
    }

    private Map<Long, GymCourseSessionDO> sessionMap(Collection<GymBookingDO> rows) {
        var ids =
                rows.stream()
                        .map(GymBookingDO::getSessionId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        return ids.isEmpty()
                ? Map.of()
                : sessions.selectByIds(ids).stream()
                        .collect(Collectors.toMap(GymCourseSessionDO::getId, Function.identity()));
    }

    private Map<Long, String> names(List<Long> values) {
        var ids = values.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> result = new HashMap<>();
        if (!ids.isEmpty())
            members.selectByIds(ids).forEach(row -> result.put(row.getId(), row.getName()));
        return result;
    }

    private void validate(Dates req) {
        access.requireAdmin();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        if (!req.getEndDate().isAfter(req.getStartDate())) throw exception(BUSINESS_DATE_INVALID);
        if (req.getStoreId() != null) scope.checkListStore(req.getStoreId());
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    private static String time(Instant value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }
}
