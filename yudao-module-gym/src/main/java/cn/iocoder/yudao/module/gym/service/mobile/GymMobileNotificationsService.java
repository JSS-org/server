package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileNotificationsVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymMobileNotificationsService {
    @Resource private GymMobileNotificationMapper notifications;
    @Resource private GymMobileNotificationPreferenceMapper preferences;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileWorkAccess work;
    @Resource private GymMemberCoachService coaches;
    @Resource private PermissionApi permissions;
    @Resource private GymBusinessClock clock;
    @Resource private GymBusinessMutex mutex;
    @Resource private Validator validator;

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private String time(Instant at) {
        return at == null ? null : at.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }

    private LambdaQueryWrapper<GymMobileNotificationDO> authorized(boolean staff) {
        var q = new LambdaQueryWrapper<GymMobileNotificationDO>();
        if (!staff) {
            var identity = identities.requireIdentity();
            return q.eq(GymMobileNotificationDO::getRecipientType, 1)
                    .eq(GymMobileNotificationDO::getRecipientUserId, identity.getUserId())
                    .eq(GymMobileNotificationDO::getMemberId, identity.getMemberId());
        }
        Long user = work.requireAdmin();
        q.eq(GymMobileNotificationDO::getRecipientType, 2)
                .eq(GymMobileNotificationDO::getRecipientUserId, user);
        // Currently staff notifications originate only from their teaching bookings. Re-evaluate
        // the original read permission and BOTH member responsibility and coach scope before
        // paging.
        if (!permissions.hasAnyPermissions(user, "gym:bookings:read")) return q.apply("1=0");
        return q.eq(GymMobileNotificationDO::getSourceType, "booking")
                .apply(
                        coaches.predicate(
                                "gym_mobile_notification.member_id",
                                "gym_mobile_notification.store_id"))
                .exists(
                        "SELECT 1 FROM gym_booking b JOIN gym_course_session s ON s.id=b.session_id AND s.tenant_id=b.tenant_id "
                                + "WHERE b.id=gym_mobile_notification.business_id AND b.tenant_id={0} AND b.deleted=0 AND s.deleted=0 "
                                + "AND b.member_id=gym_mobile_notification.member_id AND b.store_id=gym_mobile_notification.store_id AND ("
                                + coaches.coachPredicate("s.coach_id", "s.store_id")
                                + ")",
                        TenantContextHolder.getRequiredTenantId());
    }

    private PageResult<Notification> page(Query req, boolean staff) {
        validate(req);
        var q =
                authorized(staff)
                        .eq(req.getType() != null, GymMobileNotificationDO::getType, req.getType())
                        .isNull(
                                Boolean.TRUE.equals(req.getUnreadOnly()),
                                GymMobileNotificationDO::getReadAt)
                        .orderByDesc(GymMobileNotificationDO::getId);
        var result = notifications.selectPage(req, q);
        return new PageResult<>(
                result.getList().stream()
                        .map(
                                r ->
                                        new Notification(
                                                r.getId().toString(),
                                                r.getType(),
                                                r.getTitle(),
                                                r.getContent(),
                                                r.getTargetId().toString(),
                                                r.getStoreId().toString(),
                                                time(r.getPublishedAt()),
                                                time(r.getReadAt())))
                        .toList(),
                result.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Notification> memberPage(Query req) {
        return page(req, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Notification> staffPage(Query req) {
        return page(req, true);
    }

    private boolean read(Long id, boolean staff) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        var row =
                notifications.selectOne(
                        authorized(staff)
                                .eq(GymMobileNotificationDO::getId, id)
                                .last("FOR UPDATE"));
        if (row == null) throw exception(MOBILE_NOTIFICATION_NOT_EXISTS);
        if (row.getReadAt() == null)
            notifications.update(
                    null,
                    new LambdaUpdateWrapper<GymMobileNotificationDO>()
                            .eq(GymMobileNotificationDO::getId, id)
                            .isNull(GymMobileNotificationDO::getReadAt)
                            .set(GymMobileNotificationDO::getReadAt, now()));
        return true;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public boolean memberRead(Long id) {
        return read(id, false);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public boolean staffRead(Long id) {
        return read(id, true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Saved preferences(Preferences req) {
        var identity = identities.requireIdentity();
        validate(req);
        var keys = new TreeSet<>(req.getTemplateKeys());
        if (keys.size() != req.getTemplateKeys().size()) throw exception(MOBILE_REQUEST_INVALID);
        mutex.lockStoreEntitlements();
        for (String key : keys) {
            var row =
                    preferences.selectOne(
                            new LambdaQueryWrapper<GymMobileNotificationPreferenceDO>()
                                    .eq(
                                            GymMobileNotificationPreferenceDO::getUserId,
                                            identity.getUserId())
                                    .eq(GymMobileNotificationPreferenceDO::getTemplateKey, key)
                                    .last("FOR UPDATE"));
            if (row == null) {
                row = new GymMobileNotificationPreferenceDO();
                row.setUserId(identity.getUserId());
                row.setMemberId(identity.getMemberId());
                row.setTemplateKey(key);
                row.setEnabled(req.getEnabled());
                row.setSavedAt(now());
                preferences.insert(row);
            } else {
                if (!identity.getMemberId().equals(row.getMemberId()))
                    throw exception(MOBILE_IDENTITY_INVALID);
                if (!Objects.equals(row.getEnabled(), req.getEnabled())) {
                    row.setEnabled(req.getEnabled());
                    row.setSavedAt(now());
                    preferences.updateById(row);
                }
            }
        }
        return new Saved(true);
    }
}
