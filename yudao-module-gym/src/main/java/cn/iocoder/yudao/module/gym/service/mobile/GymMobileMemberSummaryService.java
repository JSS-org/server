package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.BUSINESS_DATE_INVALID;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileMemberSummaryVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileMemberSummaryAggregate.Criteria;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.temporal.ChronoUnit;

@Service
public class GymMobileMemberSummaryService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private GymStoreMapper stores;
    @Resource private GymMobileMemberSummaryMapper reports;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summary(Query req) {
        access.requireAdmin();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        if (req.getDate().getYear() < 1000 || req.getDate().getYear() > 9998)
            throw exception(BUSINESS_DATE_INVALID);
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        var allowed = scope.readableStoreIds();
        var now = clock.now().truncatedTo(ChronoUnit.MICROS);
        String updated = now.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
        if (allowed != null && allowed.isEmpty())
            return new Summary(0, 0, 0, 0, 0, req.getDate().toString(), updated);
        var ids =
                stores
                        .selectList(
                                new LambdaQueryWrapper<GymStoreDO>()
                                        .eq(
                                                req.getStoreId() != null,
                                                GymStoreDO::getId,
                                                req.getStoreId())
                                        .in(allowed != null, GymStoreDO::getId, allowed))
                        .stream()
                        .map(GymStoreDO::getId)
                        .toList();
        if (ids.isEmpty()) return new Summary(0, 0, 0, 0, 0, req.getDate().toString(), updated);
        var q =
                new Criteria(
                        TenantContextHolder.getRequiredTenantId(),
                        ids,
                        now,
                        now.plus(30, ChronoUnit.DAYS),
                        req.getDate().atStartOfDay(GymDateRange.ZONE).toInstant(),
                        req.getDate().plusDays(1).atStartOfDay(GymDateRange.ZONE).toInstant());
        var r = reports.summary(q);
        return new Summary(
                r.getMemberCount(),
                r.getPrivateCourseMemberCount(),
                r.getNewMemberCount(),
                r.getExpiringCardCount(),
                r.getExpiringCourseCount(),
                req.getDate().toString(),
                updated);
    }
}
