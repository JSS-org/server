package cn.iocoder.yudao.module.gym.service.course;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Shared ledger rules after caller authorization. Callers hold member/session/booking locks. */
@Service
public class GymBookingRules {
    @Resource private GymEmployeeMapper employees;
    @Resource private GymEmployeeStoreMapper employeeStores;
    @Resource private GymBookingMapper bookings;
    @Resource private GymCourseSessionMapper sessions;
    @Resource private GymCoursePackMapper packs;
    @Resource private GymMemberCardMapper cards;
    @Resource private GymBusinessClock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public GymEmployeeDO requireCoach(Long id, Long storeId) {
        var employee = employees.selectForUpdate(id);
        var relation =
                employeeStores.selectList(
                        new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                .eq(GymEmployeeStoreDO::getEmployeeId, id)
                                .eq(GymEmployeeStoreDO::getStoreId, storeId)
                                .last("FOR UPDATE"));
        var today = clock.now().atZone(GymDateRange.ZONE).toLocalDate();
        if (employee == null
                || !"active".equals(employee.getStatus())
                || relation.isEmpty()
                || employee.getHiredOn().isAfter(today)
                || relation.getFirst().getJoinedOn().isAfter(today))
            throw exception(COURSE_COACH_INVALID);
        return employee;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rejectOverlappingSession(
            Long coachId, java.time.Instant startsAt, java.time.Instant endsAt, Long excluding) {
        // All scheduling callers acquire this same coach lock before testing overlaps.
        var conflicts =
                sessions.selectList(
                        new LambdaQueryWrapper<GymCourseSessionDO>()
                                .eq(GymCourseSessionDO::getCoachId, coachId)
                                .ne(GymCourseSessionDO::getStatus, "cancelled")
                                .ne(excluding != null, GymCourseSessionDO::getId, excluding)
                                .lt(GymCourseSessionDO::getStartsAt, endsAt)
                                .gt(GymCourseSessionDO::getEndsAt, startsAt)
                                .last("FOR UPDATE"));
        if (!conflicts.isEmpty()) throw exception(COURSE_SESSION_CONFLICT);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rejectOverlappingBooking(Long memberId, GymCourseSessionDO session) {
        var conflicts =
                bookings.selectList(
                        new LambdaQueryWrapper<GymBookingDO>()
                                .eq(GymBookingDO::getMemberId, memberId)
                                .in(GymBookingDO::getStatus, "confirmed", "attended")
                                .ne(GymBookingDO::getSessionId, session.getId())
                                .exists(
                                        "SELECT 1 FROM gym_course_session s WHERE s.id=gym_booking.session_id "
                                                + "AND s.tenant_id={0} AND s.deleted=0 AND s.starts_at < {1} AND s.ends_at > {2}",
                                        TenantContextHolder.getRequiredTenantId(),
                                        session.getEndsAt(),
                                        session.getStartsAt())
                                .last("FOR UPDATE"));
        if (!conflicts.isEmpty()) throw exception(MOBILE_BOOKING_TIME_CONFLICT);
    }

    public void validatePack(GymCoursePackDO pack, Long memberId, GymCourseSessionDO session) {
        if (pack == null) throw exception(COURSE_PACK_NOT_EXISTS);
        if (!memberId.equals(pack.getMemberId())
                || !session.getStoreId().equals(pack.getStoreId())
                || !session.getCourseProductId().equals(pack.getCourseProductId())
                || !session.getCoachId().equals(pack.getAssignedCoachId())
                || !"active".equals(pack.getStatus())
                || pack.getStartsAt().isAfter(session.getStartsAt())
                || pack.getExpiresAt() != null && !pack.getExpiresAt().isAfter(session.getEndsAt()))
            throw exception(COURSE_PACK_INVALID);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reservePack(GymCoursePackDO pack) {
        if (pack.getRemainingSessions() - pack.getReservedSessions() < 1)
            throw exception(COURSE_PACK_INSUFFICIENT);
        pack.setReservedSessions(pack.getReservedSessions() + 1);
        packs.updateById(pack);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(GymBookingDO booking, GymCourseSessionDO session) {
        if (!Objects.equals(booking.getSessionId(), session.getId()))
            throw exception(BOOKING_INVALID);
        if ("cancelled".equals(booking.getStatus())) return;
        if (!"confirmed".equals(booking.getStatus())) throw exception(BOOKING_INVALID);
        if (booking.getMemberCoursePackId() != null) {
            var pack = packs.selectForUpdate(booking.getMemberCoursePackId());
            if (pack == null
                    || !booking.getMemberId().equals(pack.getMemberId())
                    || pack.getReservedSessions() < 1) throw exception(COURSE_PACK_INVALID);
            pack.setReservedSessions(pack.getReservedSessions() - 1);
            packs.updateById(pack);
        } else {
            var card = cards.selectForUpdate(booking.getMemberCardId());
            if (card == null
                    || !booking.getMemberId().equals(card.getMemberId())
                    || card.getReservedUses() < 1) throw exception(CARD_RIGHT_INVALID);
            card.setReservedUses(card.getReservedUses() - 1);
            cards.updateById(card);
        }
        if (session.getReservedCount() < 1) throw exception(COURSE_SESSION_INVALID);
        booking.setStatus("cancelled");
        booking.setCancelledAt(clock.now().truncatedTo(ChronoUnit.MICROS));
        bookings.updateById(booking);
        session.setReservedCount(session.getReservedCount() - 1);
        sessions.updateById(session);
    }
}
