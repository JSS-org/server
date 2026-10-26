package cn.iocoder.yudao.module.gym.service.marketing;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.module.gym.dal.dataobject.marketing.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.marketing.GymActivityRegistrationMapper;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessClock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Caller must authorize the member/store and hold activity then member locks. */
@Service
public class GymActivityParticipationService {
    @Resource
    private cn.iocoder.yudao.module.gym.service.mobile.GymMobileNotificationPublisher notifications;

    @Resource private GymActivityRegistrationMapper gymActivityRegistrationMapper;
    @Resource private GymBusinessClock clock;

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private long confirmed(Long activity) {
        return gymActivityRegistrationMapper.selectCount(
                new LambdaQueryWrapper<GymActivityRegistrationDO>()
                        .eq(GymActivityRegistrationDO::getActivityId, activity)
                        .eq(GymActivityRegistrationDO::getStatus, "confirmed"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public GymActivityRegistrationDO register(
            GymActivityDO row, GymMemberDO member, GymStoreDO store) {
        if (!"active".equals(member.getStatus()) || !"active".equals(store.getStatus()))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        var registration =
                gymActivityRegistrationMapper.selectOne(
                        new LambdaQueryWrapper<GymActivityRegistrationDO>()
                                .eq(GymActivityRegistrationDO::getActivityId, row.getId())
                                .eq(GymActivityRegistrationDO::getMemberId, member.getId()));
        if (registration != null && !store.getId().equals(registration.getStoreId()))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        if (registration != null && "confirmed".equals(registration.getStatus()))
            return registration;
        Instant now = now();
        if (!"published".equals(row.getStatus())
                || now.isBefore(row.getRegistrationStartsAt())
                || !now.isBefore(row.getRegistrationEndsAt()))
            throw exception(ACTIVITY_REGISTRATION_INVALID);
        if (row.getCapacity() != null && confirmed(row.getId()) >= row.getCapacity())
            throw exception(ACTIVITY_CAPACITY_INVALID);
        boolean create = registration == null;
        if (create) registration = new GymActivityRegistrationDO();
        registration.setActivityId(row.getId());
        registration.setMemberId(member.getId());
        registration.setMemberName(member.getName());
        registration.setStoreId(store.getId());
        registration.setStoreName(store.getName());
        registration.setStatus("confirmed");
        registration.setRegisteredAt(now);
        registration.setCancelledAt(null);
        if (create) gymActivityRegistrationMapper.insert(registration);
        else gymActivityRegistrationMapper.updateById(registration);
        notifications.activity(registration.getId());
        return registration;
    }
}
