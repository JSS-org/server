package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDashboardVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileDashboardAggregate.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Service
public class GymMobileDashboardService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private PermissionApi permissions;
    @Resource private SecurityFrameworkService security;
    @Resource private GymBusinessClock clock;
    @Resource private GymStoreMapper stores;
    @Resource private GymEmployeeStoreMapper employments;
    @Resource private GymMobileDashboardMapper mapper;
    @Resource private Validator validator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dashboard dashboard(Query req) {
        Long user = access.requireAdmin();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        var now = clock.now().truncatedTo(ChronoUnit.MICROS);
        var today = now.atZone(GymDateRange.ZONE).toLocalDate();
        LocalDate date = req.getDate() == null ? today : req.getDate();
        if (date.isBefore(LocalDate.of(1000, 1, 2)) || date.getYear() > 9998 || date.isAfter(today))
            throw exception(BUSINESS_DATE_INVALID);
        var allowed = scope.readableStoreIds();
        Long employee = null;
        if (allowed != null
                && (allowed.isEmpty()
                        || req.getStoreId() != null && !allowed.contains(req.getStoreId()))) {
            var permission = permissions.getDeptDataPermission(user);
            if (permission == null || !Boolean.TRUE.equals(permission.getSelf()))
                throw exception(STORE_NO_PERMISSION);
            employee = access.employee();
            allowed =
                    employments
                            .selectList(
                                    new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                            .eq(GymEmployeeStoreDO::getEmployeeId, employee)
                                            .le(GymEmployeeStoreDO::getJoinedOn, today))
                            .stream()
                            .map(GymEmployeeStoreDO::getStoreId)
                            .collect(Collectors.toSet());
            if (allowed.isEmpty()
                    || req.getStoreId() != null && !allowed.contains(req.getStoreId()))
                throw exception(STORE_NO_PERMISSION);
        } else if (req.getStoreId() != null) scope.check(req.getStoreId());
        var ids =
                stores
                        .selectList(
                                new LambdaQueryWrapper<GymStoreDO>()
                                        .eq(
                                                req.getStoreId() != null,
                                                GymStoreDO::getId,
                                                req.getStoreId())
                                        .in(allowed != null, GymStoreDO::getId, allowed)
                                        .eq(employee != null, GymStoreDO::getStatus, "active")
                                        .orderByAsc(GymStoreDO::getId))
                        .stream()
                        .map(GymStoreDO::getId)
                        .toList();
        if (employee != null && ids.isEmpty()) throw exception(STORE_NO_PERMISSION);
        var yesterday = date.minusDays(1).atStartOfDay(GymDateRange.ZONE).toInstant();
        var month = date.withDayOfMonth(1).atStartOfDay(GymDateRange.ZONE).toInstant();
        var q =
                new Criteria(
                        TenantContextHolder.getRequiredTenantId(),
                        ids,
                        user,
                        employee,
                        now,
                        yesterday.isBefore(month) ? yesterday : month,
                        yesterday,
                        date.atStartOfDay(GymDateRange.ZONE).toInstant(),
                        date.plusDays(1).atStartOfDay(GymDateRange.ZONE).toInstant(),
                        month);
        Performance performance = null;
        Checkins checkins = null;
        Lessons lessons = null;
        Members members = null;
        Long pending = null;
        if (security.hasPermission("gym:reports:read")) {
            if (ids.isEmpty()) performance = new Performance("0.00", "0.00");
            else {
                var r = mapper.amounts(q);
                performance = new Performance(money(r.getTodayAmount()), money(r.getMonthAmount()));
            }
        }
        if (security.hasPermission("gym:checkins:read")) {
            var r = ids.isEmpty() ? new Counts() : mapper.checkins(q);
            checkins = new Checkins(r.getTodayCount(), r.getYesterdayCount());
        }
        if (security.hasPermission("gym:lessons:read")) {
            var r = ids.isEmpty() ? new Counts() : mapper.lessons(q);
            lessons = new Lessons(r.getTodayCount(), r.getYesterdayCount(), r.getMonthCount());
        }
        if (security.hasPermission("gym:members:read")) {
            var r = ids.isEmpty() ? new Population() : mapper.members(q);
            members =
                    new Members(
                            r.getNewTodayCount(),
                            employee == null ? null : r.getTotal(),
                            employee == null ? r.getTotal() : null);
        }
        if (employee == null
                && security.hasPermission("gym:approvals:read")
                && security.hasPermission("gym:approvals:decide"))
            pending = ids.isEmpty() ? 0L : mapper.pending(q);
        return new Dashboard(
                new Scope(
                        employee == null ? "store" : "self",
                        ids.stream().map(String::valueOf).toList(),
                        employee == null ? null : employee.toString()),
                date.toString(),
                performance,
                checkins,
                lessons,
                members,
                pending,
                now.atZone(GymDateRange.ZONE).toOffsetDateTime().toString());
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
