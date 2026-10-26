package cn.iocoder.yudao.module.gym.service.course;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Authorized callers hold session then booking locks; every confirmation shares one ledger. */
@Service
public class GymLessonConfirmationService {
    @Resource private GymLessonRecordMapper lessons;
    @Resource private GymCheckinMapper checkins;
    @Resource private GymCoursePackMapper packs;
    @Resource private GymMemberCardMapper cards;
    @Resource private GymBookingMapper bookings;
    @Resource private GymCourseSessionMapper sessions;
    @Resource private GymBookingRules rules;
    @Resource private GymMemberRecordsService records;
    @Resource private GymBusinessClock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public GymLessonRecordDO confirm(
            GymBookingDO booking, GymCourseSessionDO session, String source) {
        if (!booking.getSessionId().equals(session.getId())
                || !booking.getStoreId().equals(session.getStoreId())
                || !("manual".equals(source) || "qr".equals(source)))
            throw exception(BOOKING_INVALID);
        var previous =
                lessons.selectOne(
                        new LambdaQueryWrapper<GymLessonRecordDO>()
                                .eq(GymLessonRecordDO::getBookingId, booking.getId())
                                .last("FOR UPDATE"));
        if (previous != null) {
            if (!"confirmed".equals(previous.getStatus())
                    || !booking.getMemberId().equals(previous.getMemberId())
                    || !booking.getStoreId().equals(previous.getStoreId())
                    || !session.getCoachId().equals(previous.getCoachId()))
                throw exception(BOOKING_INVALID);
            return previous;
        }
        if (!"confirmed".equals(booking.getStatus())) throw exception(BOOKING_INVALID);
        var checkin =
                booking.getCheckinId() == null ? null : checkins.selectById(booking.getCheckinId());
        if (checkin == null
                || !Objects.equals(booking.getId(), checkin.getBookingId())
                || !booking.getMemberId().equals(checkin.getMemberId())
                || !booking.getStoreId().equals(checkin.getStoreId())
                || !"succeeded".equals(checkin.getStatus()))
            throw exception(LESSON_CHECKIN_REQUIRED);
        var now = clock.now().truncatedTo(ChronoUnit.MICROS);
        if (now.isBefore(session.getStartsAt())
                || checkin.getEnteredAt().isAfter(now)
                || !checkin.getEnteredAt()
                        .atZone(GymDateRange.ZONE)
                        .toLocalDate()
                        .equals(session.getStartsAt().atZone(GymDateRange.ZONE).toLocalDate()))
            throw exception(LESSON_TIME_INVALID);
        records.checkEntry(booking.getMemberId());
        Integer remaining;
        if (booking.getMemberCoursePackId() != null) {
            var pack = packs.selectForUpdate(booking.getMemberCoursePackId());
            rules.validatePack(pack, booking.getMemberId(), session);
            if (pack.getRemainingSessions() < 1 || pack.getReservedSessions() < 1)
                throw exception(COURSE_PACK_INSUFFICIENT);
            pack.setRemainingSessions(pack.getRemainingSessions() - 1);
            pack.setReservedSessions(pack.getReservedSessions() - 1);
            packs.updateById(pack);
            remaining = pack.getRemainingSessions();
        } else {
            var card = cards.selectForUpdate(booking.getMemberCardId());
            if (card == null) throw exception(CARD_NOT_EXISTS);
            if (!booking.getMemberId().equals(card.getMemberId())
                    || !"active".equals(card.getStatus())
                    || card.getStartsAt().isAfter(session.getStartsAt())
                    || card.getExpiresAt() != null
                            && !card.getExpiresAt().isAfter(session.getEndsAt())
                    || !"all".equals(card.getAccessScope())
                            && !JsonUtils.parseArray(card.getStoreIds(), Long.class)
                                    .contains(booking.getStoreId()))
                throw exception(CARD_RIGHT_INVALID);
            if (card.getReservedUses() < 1
                    || card.getRemainingUses() != null && card.getRemainingUses() < 1)
                throw exception(CARD_RIGHT_INSUFFICIENT);
            card.setReservedUses(card.getReservedUses() - 1);
            if (card.getRemainingUses() != null) card.setRemainingUses(card.getRemainingUses() - 1);
            cards.updateById(card);
            remaining = card.getRemainingUses();
        }
        var lesson = new GymLessonRecordDO();
        lesson.setStoreId(booking.getStoreId());
        lesson.setMemberId(booking.getMemberId());
        lesson.setCoachId(session.getCoachId());
        lesson.setBookingId(booking.getId());
        lesson.setCheckinId(checkin.getId());
        lesson.setMemberCardId(booking.getMemberCardId());
        lesson.setMemberCoursePackId(booking.getMemberCoursePackId());
        lesson.setSessionUnits(1);
        lesson.setRemainingLessonsAfter(remaining);
        lesson.setConfirmedAt(now);
        lesson.setMethod(source);
        lesson.setStatus("confirmed");
        lessons.insert(lesson);
        booking.setStatus("attended");
        bookings.updateById(booking);
        session.setStatus("completed");
        sessions.updateById(session);
        return lesson;
    }
}
