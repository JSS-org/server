package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymMemberCoachesReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkMembersVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMemberFollowupDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMemberFollowupMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
public class GymMobileWorkMembersService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymMemberFollowupMapper followups;
    @Resource private GymMemberCoachService coaches;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymMemberMapper members;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    public PageResult<Followup> followups(Long member, FollowupPage req) {
        validate(req);
        access.member(member, req.getStoreId());
        var page =
                followups.selectPage(
                        req,
                        new LambdaQueryWrapper<GymMemberFollowupDO>()
                                .eq(GymMemberFollowupDO::getMemberId, member)
                                .eq(GymMemberFollowupDO::getStoreId, req.getStoreId())
                                .orderByDesc(
                                        GymMemberFollowupDO::getCreateTime,
                                        GymMemberFollowupDO::getId));
        return new PageResult<>(
                page.getList().stream().map(this::response).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Followup createFollowup(Long member, FollowupCreate req, String key) {
        validate(req);
        access.member(member, req.getStoreId());
        Long employee = access.employee();
        return idempotency.execute(
                "work-followup:" + member,
                key,
                req,
                Followup.class,
                () -> {
                    if (req.getNextContactAt() != null
                            && !req.getNextContactAt().isAfter(clock.now()))
                        throw exception(MOBILE_REQUEST_INVALID);
                    var row = new GymMemberFollowupDO();
                    row.setMemberId(member);
                    row.setStoreId(req.getStoreId());
                    row.setEmployeeId(employee);
                    row.setContent(req.getContent());
                    row.setNextContactAt(
                            req.getNextContactAt() == null
                                    ? null
                                    : req.getNextContactAt().truncatedTo(ChronoUnit.MICROS));
                    followups.insert(row);
                    return response(followups.selectById(row.getId()));
                });
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public AssignmentResult assign(Long member, Assignment req, String key) {
        validate(req);
        access.member(member, req.getStoreId());
        scope.check(req.getStoreId());
        return idempotency.execute(
                "work-assign:" + member,
                key,
                req,
                AssignmentResult.class,
                () -> {
                    // Match the legacy assignment lock order so both entry points share one
                    // version.
                    mutex.lockStoreEntitlements();
                    members.selectForUpdate(member);
                    var relation =
                            memberships.selectOne(
                                    new LambdaQueryWrapper<GymMemberStoreDO>()
                                            .eq(GymMemberStoreDO::getMemberId, member)
                                            .eq(GymMemberStoreDO::getStoreId, req.getStoreId())
                                            .last("FOR UPDATE"));
                    if (relation == null) throw exception(STORE_NO_PERMISSION);
                    if (!req.getExpectedVersion().equals(relation.getAssignmentVersion()))
                        throw exception(MOBILE_VERSION_CONFLICT);
                    var legacy = new GymMemberCoachesReqVO();
                    legacy.setEmployeeIds(req.getEmployeeIds());
                    var assigned = coaches.replace(member, req.getStoreId(), legacy);
                    return new AssignmentResult(
                            member.toString(),
                            req.getStoreId().toString(),
                            assigned.stream().map(r -> r.employeeId()).toList(),
                            memberships.selectById(relation.getId()).getAssignmentVersion());
                });
    }

    private Followup response(GymMemberFollowupDO row) {
        return new Followup(
                row.getId().toString(),
                row.getMemberId().toString(),
                row.getStoreId().toString(),
                row.getEmployeeId().toString(),
                row.getContent(),
                time(row.getNextContactAt()),
                row.getCreateTime().atZone(GymDateRange.ZONE).toOffsetDateTime().toString());
    }

    private static String time(Instant value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }
}
