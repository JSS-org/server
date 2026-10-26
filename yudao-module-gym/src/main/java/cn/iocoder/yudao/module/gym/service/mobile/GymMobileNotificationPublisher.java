package cn.iocoder.yudao.module.gym.service.mobile;

import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.marketing.GymActivityRegistrationDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileNotificationDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.marketing.GymActivityRegistrationMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.*;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Internal transactional projection of actual persisted business transitions; no external delivery. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class GymMobileNotificationPublisher {
    @Resource private GymMobileNotificationMapper notifications;
    @Resource private GymMobileIdentityMapper identities;
    @Resource private GymBookingMapper bookings;
    @Resource private GymCourseSessionMapper sessions;
    @Resource private GymCoursePackMapper packs;
    @Resource private GymMemberCardMapper cards;
    @Resource private GymContractMapper contracts;
    @Resource private GymActivityRegistrationMapper registrations;
    @Resource private GymEmployeeMapper employees;
    @Resource private GymEmployeeStoreMapper employments;
    @Resource private GymBusinessClock clock;

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void publish(
            String type,
            String source,
            Long business,
            Long member,
            Long store,
            String event,
            Instant occurred,
            String title,
            String content,
            Long staffUser,
            Long targetId) {
        var latest =
                notifications.selectOne(
                        new LambdaQueryWrapper<GymMobileNotificationDO>()
                                .eq(GymMobileNotificationDO::getSourceType, source)
                                .eq(GymMobileNotificationDO::getBusinessId, business)
                                .orderByDesc(GymMobileNotificationDO::getId)
                                .last("LIMIT 1 FOR UPDATE"));
        // Each source is locked before this check. Booking/activity can repeat the same transition
        // after cancellation; the latest event distinguishes those cycles even at the same clock
        // tick.
        if (latest != null
                && event.equals(latest.getEventKind())
                && ("issued".equals(event) || Objects.equals(occurred, latest.getOccurredAt())))
            return;
        var binding =
                identities.selectOne(
                        cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileIdentityDO
                                ::getMemberId,
                        member);
        String eventKey = UUID.randomUUID().toString();
        if (binding != null)
            save(
                    1,
                    binding.getUserId(),
                    member,
                    store,
                    type,
                    source,
                    business,
                    event,
                    eventKey,
                    occurred,
                    title,
                    content,
                    targetId);
        if (staffUser != null)
            save(
                    2, staffUser, member, store, type, source, business, event, eventKey, occurred,
                    title, content, targetId);
    }

    private void save(
            int userType,
            Long user,
            Long member,
            Long store,
            String type,
            String source,
            Long business,
            String event,
            String key,
            Instant occurred,
            String title,
            String content,
            Long targetId) {
        var row = new GymMobileNotificationDO();
        row.setRecipientType(userType);
        row.setRecipientUserId(user);
        row.setMemberId(member);
        row.setStoreId(store);
        row.setType(type);
        row.setSourceType(source);
        row.setBusinessId(business);
        row.setTargetId(targetId);
        row.setEventKind(event);
        row.setEventKey(key);
        row.setOccurredAt(occurred);
        row.setTitle(title);
        row.setContent(content);
        row.setPublishedAt(now());
        notifications.insert(row);
    }

    public void booking(Long id) {
        var row = bookings.selectForUpdate(id);
        if (row == null || !Set.of("confirmed", "cancelled").contains(row.getStatus())) return;
        var session = sessions.selectById(row.getSessionId());
        if (session == null) return;
        var employee = employees.selectById(session.getCoachId());
        var today = clock.now().atZone(GymDateRange.ZONE).toLocalDate();
        Long staffUser =
                employee != null
                                && "active".equals(employee.getStatus())
                                && !employee.getHiredOn().isAfter(today)
                                && employments.selectCount(
                                                new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                                        .eq(
                                                                GymEmployeeStoreDO::getEmployeeId,
                                                                employee.getId())
                                                        .eq(
                                                                GymEmployeeStoreDO::getStoreId,
                                                                row.getStoreId())
                                                        .le(GymEmployeeStoreDO::getJoinedOn, today))
                                        > 0
                        ? employee.getUserId()
                        : null;
        boolean cancelled = "cancelled".equals(row.getStatus());
        publish(
                "booking",
                "booking",
                id,
                row.getMemberId(),
                row.getStoreId(),
                row.getStatus(),
                cancelled ? row.getCancelledAt() : row.getBookedAt(),
                cancelled ? "预约已取消" : "预约已确认",
                "请查看预约详情了解当前安排。",
                staffUser,
                id);
    }

    public void card(Long id) {
        var row = cards.selectForUpdate(id);
        if (row != null)
            publish(
                    "member_card",
                    "card",
                    id,
                    row.getMemberId(),
                    row.getIssuedStoreId(),
                    "issued",
                    now(),
                    "会员卡已发放",
                    "会员卡已记录，请查看适用门店、有效期及当前状态。",
                    null,
                    id);
    }

    public void coursePack(Long id) {
        var row = packs.selectForUpdate(id);
        if (row != null)
            publish(
                    "course_pack",
                    "course_pack",
                    id,
                    row.getMemberId(),
                    row.getStoreId(),
                    "issued",
                    now(),
                    "私教课包已发放",
                    "课包已记录，请查看课时、有效期及可约安排。",
                    null,
                    id);
    }

    public void contract(Long id) {
        var row = contracts.selectForUpdate(id);
        if (row != null && "unsigned".equals(row.getStatus()))
            publish(
                    "contract",
                    "contract",
                    id,
                    row.getMemberId(),
                    row.getStoreId(),
                    "issued",
                    row.getIssuedAt(),
                    "有一份新合同",
                    "合同已生成，尚未签署，请查看合同详情。",
                    null,
                    id);
    }

    public void activity(Long id) {
        var row =
                registrations.selectOne(
                        new LambdaQueryWrapper<GymActivityRegistrationDO>()
                                .eq(GymActivityRegistrationDO::getId, id)
                                .last("FOR UPDATE"));
        if (row == null || !Set.of("confirmed", "cancelled").contains(row.getStatus())) return;
        boolean cancelled = "cancelled".equals(row.getStatus());
        publish(
                "activity",
                "activity_registration",
                id,
                row.getMemberId(),
                row.getStoreId(),
                row.getStatus(),
                cancelled ? row.getCancelledAt() : row.getRegisteredAt(),
                cancelled ? "活动报名已取消" : "活动报名已确认",
                "请查看活动及本人报名记录了解当前安排。",
                null,
                row.getActivityId());
    }
}
