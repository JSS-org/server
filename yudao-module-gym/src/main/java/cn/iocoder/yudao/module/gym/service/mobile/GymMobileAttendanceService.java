package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileAttendanceVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCheckinDO;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCheckinMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileAttendanceMapper;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

@Service
public class GymMobileAttendanceService {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileAttendanceMapper mapper;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private GymCheckinMapper checkins;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary get(Request req) {
        Long member = identities.requireMemberId();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        YearMonth month;
        try {
            month = YearMonth.parse(req.getMonth());
            if (month.getYear() < 1900) throw new DateTimeException("unsupported year");
        } catch (DateTimeException e) {
            throw exception(BUSINESS_DATE_INVALID);
        }
        var now = clock.now().truncatedTo(ChronoUnit.MICROS);
        boolean related =
                memberships.selectCount(
                                new LambdaQueryWrapper<GymMemberStoreDO>()
                                        .eq(GymMemberStoreDO::getMemberId, member)
                                        .eq(GymMemberStoreDO::getStoreId, req.getStoreId()))
                        > 0;
        if (!related
                && checkins.selectCount(
                                new LambdaQueryWrapper<GymCheckinDO>()
                                        .eq(GymCheckinDO::getMemberId, member)
                                        .eq(GymCheckinDO::getStoreId, req.getStoreId())
                                        .eq(GymCheckinDO::getStatus, "succeeded")
                                        .le(GymCheckinDO::getEnteredAt, now))
                        == 0) throw exception(STORE_NO_PERMISSION);
        String updated = now.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
        if (month.isAfter(YearMonth.from(now.atZone(GymDateRange.ZONE))))
            return new Summary(0, 0, null, 0, updated);
        var days = new ArrayList<GymMobileAttendanceMapper.Day>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            var date = month.atDay(day);
            days.add(
                    new GymMobileAttendanceMapper.Day(
                            date.atStartOfDay(GymDateRange.ZONE).toInstant(),
                            date.plusDays(1).atStartOfDay(GymDateRange.ZONE).toInstant(),
                            day));
        }
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var from = days.getFirst().from();
        var to = days.getLast().to();
        var own = mapper.own(tenant, req.getStoreId(), member, from, to, now, days);
        var rank =
                mapper.ranking(tenant, req.getStoreId(), own.getActiveDays(), from, to, now, days);
        return new Summary(
                own.getCheckinCount(),
                own.getActiveDays(),
                own.getActiveDays() == 0 ? null : Math.addExact(rank.getAhead(), 1),
                rank.getPopulation(),
                updated);
    }
}
