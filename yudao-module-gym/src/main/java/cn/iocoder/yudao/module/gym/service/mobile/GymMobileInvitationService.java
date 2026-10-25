package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileInvitationVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileInvitationDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileInvitationMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileInvitationProperties;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class GymMobileInvitationService {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileInvitationMapper invitations;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymStoreMapper stores;
    @Resource private GymMobileInvitationProperties properties;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    private void validate(Object value) {
        var errors = validator.validate(value);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private void eligible(Long member, Long store) {
        var location = stores.selectById(store);
        if (location == null
                || !"active".equals(location.getStatus())
                || memberships.selectCount(
                                new LambdaQueryWrapper<GymMemberStoreDO>()
                                        .eq(GymMemberStoreDO::getMemberId, member)
                                        .eq(GymMemberStoreDO::getStoreId, store))
                        == 0) throw exception(MOBILE_INVITATION_UNAVAILABLE);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Invitation create(Create req, String key) {
        validate(req);
        var identity = identities.requireIdentity();
        eligible(identity.getMemberId(), req.getStoreId());
        return idempotency.execute(
                "invitation-create",
                key,
                req,
                Invitation.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    var current = identities.requireIdentity();
                    eligible(current.getMemberId(), req.getStoreId());
                    validate(properties);
                    var row = new GymMobileInvitationDO();
                    row.setStoreId(req.getStoreId());
                    row.setInviterMemberId(current.getMemberId());
                    row.setInviterUserId(current.getUserId());
                    row.setInviteCode(UUID.randomUUID().toString().replace("-", ""));
                    row.setSharePage(properties.getSharePage());
                    row.setIssuedAt(clock.now().truncatedTo(ChronoUnit.MICROS));
                    row.setExpiresAt(
                            row.getIssuedAt().plus(properties.getValidDays(), ChronoUnit.DAYS));
                    invitations.insert(row);
                    return new Invitation(
                            row.getInviteCode(),
                            row.getSharePage()
                                    + "?storeId="
                                    + row.getStoreId()
                                    + "&inviteCode="
                                    + row.getInviteCode(),
                            row.getExpiresAt()
                                    .atZone(GymDateRange.ZONE)
                                    .toOffsetDateTime()
                                    .toString());
                });
    }
}
