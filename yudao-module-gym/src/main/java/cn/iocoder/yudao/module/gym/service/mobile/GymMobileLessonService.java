package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.LessonRecord;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileLessonVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileLessonQrDO;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberCoachMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileLessonQrMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileLessonProperties;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.course.GymLessonConfirmationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class GymMobileLessonService {
    @Resource private GymMobileWorkAccess workAccess;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymMobileLessonToken tokens;
    @Resource private GymMobileLessonProperties properties;
    @Resource private GymMobileLessonQrMapper qrs;
    @Resource private GymCourseSessionMapper sessions;
    @Resource private GymBookingMapper bookings;
    @Resource private GymMemberCoachMapper responsibilities;
    @Resource private GymStoreMapper stores;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private GymBusinessClock clock;
    @Resource private GymLessonConfirmationService confirmations;
    @Resource private Validator validator;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Qr issue(QrCreate req, String key) {
        validate(req);
        Long issuer = workAccess.requireAdmin();
        var initial = sessions.selectById(req.getSessionId());
        if (initial == null) throw exception(COURSE_SESSION_NOT_EXISTS);
        var target =
                bookings.selectOne(
                        new LambdaQueryWrapper<GymBookingDO>()
                                .eq(GymBookingDO::getSessionId, initial.getId())
                                .in(GymBookingDO::getStatus, "confirmed", "attended"));
        if (target == null) throw exception(BOOKING_NOT_EXISTS);
        authorizeIssuer(issuer, initial, target);
        return idempotency.execute(
                "work-lesson-qr",
                key,
                req,
                Qr.class,
                () -> {
                    var session = sessions.selectForUpdate(initial.getId());
                    var booking = bookings.selectForUpdate(target.getId());
                    if (session == null
                            || booking == null
                            || !"scheduled".equals(session.getStatus())
                            || !"confirmed".equals(booking.getStatus())
                            || !booking.getSessionId().equals(session.getId())
                            || !booking.getStoreId().equals(session.getStoreId()))
                        throw exception(BOOKING_INVALID);
                    authorizeIssuer(issuer, session, booking);
                    var now = now();
                    window(session, now);
                    activeStore(session.getStoreId());
                    String token = tokens.create();
                    var expires = now.plusSeconds(properties.getQrTtlSeconds());
                    var cutoff = cutoff(session);
                    if (expires.isAfter(cutoff)) expires = cutoff;
                    var qr = new GymMobileLessonQrDO();
                    qr.setTokenHash(DigestUtil.sha256Hex(token));
                    qr.setIssuerUserId(issuer);
                    qr.setStoreId(session.getStoreId());
                    qr.setCoachId(session.getCoachId());
                    qr.setSessionId(session.getId());
                    qr.setBookingId(booking.getId());
                    qr.setMemberId(booking.getMemberId());
                    qr.setBookingBookedAt(booking.getBookedAt());
                    qr.setSessionStartsAt(session.getStartsAt());
                    qr.setSessionEndsAt(session.getEndsAt());
                    qr.setExpiresAt(expires);
                    qrs.insert(qr);
                    return new Qr(token, time(expires));
                });
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public LessonRecord confirm(Confirm req, String key) {
        validate(req);
        Long member = identities.requireMemberId();
        var initial = owned(req.getBookingId(), member, false);
        return idempotency.execute(
                "lesson-confirm",
                key,
                req,
                LessonRecord.class,
                () -> {
                    tokens.verify(req.getQrToken());
                    var session = sessions.selectForUpdate(initial.getSessionId());
                    var booking = owned(req.getBookingId(), member, true);
                    var qr =
                            qrs.selectOne(
                                    new LambdaQueryWrapper<GymMobileLessonQrDO>()
                                            .eq(
                                                    GymMobileLessonQrDO::getTokenHash,
                                                    DigestUtil.sha256Hex(req.getQrToken()))
                                            .last("FOR UPDATE"));
                    var now = now();
                    if (session == null
                            || qr == null
                            || !now.isBefore(qr.getExpiresAt())
                            || !qr.getBookingId().equals(booking.getId())
                            || !qr.getMemberId().equals(member)
                            || !qr.getSessionId().equals(session.getId())
                            || !qr.getStoreId().equals(session.getStoreId())
                            || !qr.getCoachId().equals(session.getCoachId())
                            || !Objects.equals(qr.getBookingBookedAt(), booking.getBookedAt())
                            || !qr.getSessionStartsAt().equals(session.getStartsAt())
                            || !qr.getSessionEndsAt().equals(session.getEndsAt()))
                        throw exception(MOBILE_LESSON_QR_INVALID);
                    authorizeIssuer(qr.getIssuerUserId(), session, booking);
                    window(session, now);
                    activeStore(session.getStoreId());
                    var lesson = confirmations.confirm(booking, session, "qr");
                    if (qr.getUsedAt() == null) {
                        qr.setUsedAt(now);
                        qr.setLessonRecordId(lesson.getId());
                        qrs.updateById(qr);
                    }
                    return new LessonRecord(
                            lesson.getId().toString(),
                            lesson.getBookingId().toString(),
                            lesson.getCheckinId().toString(),
                            time(lesson.getConfirmedAt()),
                            lesson.getMethod(),
                            lesson.getSessionUnits(),
                            lesson.getRemainingLessonsAfter());
                });
    }

    private GymBookingDO owned(Long id, Long member, boolean lock) {
        var q =
                new LambdaQueryWrapper<GymBookingDO>()
                        .eq(GymBookingDO::getId, id)
                        .eq(GymBookingDO::getMemberId, member);
        if (lock) q.last("FOR UPDATE");
        var row = bookings.selectOne(q);
        if (row == null) throw exception(BOOKING_NOT_EXISTS);
        return row;
    }

    private void authorizeIssuer(Long issuer, GymCourseSessionDO session, GymBookingDO booking) {
        var user = users.getUser(issuer);
        if (user == null
                || !Integer.valueOf(0).equals(user.getStatus())
                || !permissions.hasAnyPermissions(issuer, "gym:bookings:write"))
            throw exception(MOBILE_LESSON_QR_INVALID);
        var scope = permissions.getDeptDataPermission(issuer);
        if (scope != null
                && (Boolean.TRUE.equals(scope.getAll())
                        || scope.getDeptIds() != null
                                && scope.getDeptIds().contains(session.getStoreId()))) return;
        if (scope == null
                || !Boolean.TRUE.equals(scope.getSelf())
                || responsibilities
                        .currentResponsibilities(
                                TenantContextHolder.getRequiredTenantId(),
                                issuer,
                                clock.now().atZone(GymDateRange.ZONE).toLocalDate())
                        .stream()
                        .noneMatch(
                                r ->
                                        r.getEmployeeId().equals(session.getCoachId())
                                                && r.getStoreId().equals(session.getStoreId())
                                                && r.getMemberId().equals(booking.getMemberId())))
            throw exception(MOBILE_LESSON_QR_INVALID);
    }

    private void activeStore(Long id) {
        var store = stores.selectById(id);
        if (store == null || !"active".equals(store.getStatus()))
            throw exception(MEMBER_STORE_INACTIVE);
    }

    private Instant cutoff(GymCourseSessionDO session) {
        return session.getEndsAt().plus(properties.getConfirmAfterEndMinutes(), ChronoUnit.MINUTES);
    }

    private void window(GymCourseSessionDO session, Instant now) {
        if (now.isBefore(session.getStartsAt()) || !now.isBefore(cutoff(session)))
            throw exception(LESSON_TIME_INVALID);
    }

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String time(Instant value) {
        return value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }
}
