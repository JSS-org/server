package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobilePerformanceVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobilePerformanceAggregate.*;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMobilePerformanceService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private PermissionApi permissions;
    @Resource private GymBusinessClock clock;
    @Resource private GymStoreMapper stores;
    @Resource private GymEmployeeMapper employees;
    @Resource private GymEmployeeStoreMapper assignments;
    @Resource private GymMobilePerformanceMapper reports;
    @Resource private Validator validator;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String average(BigDecimal value, long n) {
        return n == 0
                ? "0.00"
                : money(value.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
    }

    private static final class Bucket {
        Long id;
        String name;
        BigDecimal net = ZERO;
        long orders;
        long lessons;

        Bucket(Long id) {
            this.id = id;
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result performance(Query req) {
        Long user = access.requireAdmin();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        long days = ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate());
        if (req.getStartDate().getYear() < 1000
                || req.getEndDate().getYear() > 9999
                || days < 1
                || days > 366) throw exception(BUSINESS_DATE_INVALID);
        LocalDate today = clock.now().atZone(GymDateRange.ZONE).toLocalDate();
        var allowed = scope.readableStoreIds();
        Long self = null;
        if (allowed != null
                && (allowed.isEmpty()
                        || req.getStoreId() != null && !allowed.contains(req.getStoreId()))) {
            var permission = permissions.getDeptDataPermission(user);
            if (permission == null
                    || !Boolean.TRUE.equals(permission.getSelf())
                    || !"employee".equals(req.getGroupBy())) throw exception(STORE_NO_PERMISSION);
            self = access.employee();
            allowed =
                    assignments
                            .selectList(
                                    new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                            .eq(GymEmployeeStoreDO::getEmployeeId, self)
                                            .le(GymEmployeeStoreDO::getJoinedOn, today))
                            .stream()
                            .map(GymEmployeeStoreDO::getStoreId)
                            .collect(Collectors.toSet());
            if (allowed.isEmpty()
                    || req.getStoreId() != null && !allowed.contains(req.getStoreId()))
                throw exception(STORE_NO_PERMISSION);
        } else if (req.getStoreId() != null) scope.check(req.getStoreId());
        var selected =
                stores.selectList(
                        new LambdaQueryWrapper<GymStoreDO>()
                                .eq(req.getStoreId() != null, GymStoreDO::getId, req.getStoreId())
                                .in(allowed != null, GymStoreDO::getId, allowed)
                                .eq(self != null, GymStoreDO::getStatus, "active"));
        var ids = selected.stream().map(GymStoreDO::getId).toList();
        if (ids.isEmpty())
            return new Result(List.of(), 0, new Summary("0.00", 0, 0, "0.00", "0.00", 0));
        var q =
                new Criteria(
                        TenantContextHolder.getRequiredTenantId(),
                        ids,
                        req.getStartDate().atStartOfDay(GymDateRange.ZONE).toInstant(),
                        req.getEndDate().atStartOfDay(GymDateRange.ZONE).toInstant(),
                        self,
                        true);
        var links =
                assignments.selectList(
                        new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                .in(GymEmployeeStoreDO::getStoreId, ids)
                                .le(GymEmployeeStoreDO::getJoinedOn, today)
                                .lt(GymEmployeeStoreDO::getJoinedOn, req.getEndDate())
                                .eq(self != null, GymEmployeeStoreDO::getEmployeeId, self));
        var population = new HashSet<Long>();
        var linked = links.stream().map(GymEmployeeStoreDO::getEmployeeId).distinct().toList();
        if (!linked.isEmpty())
            employees
                    .selectList(
                            new LambdaQueryWrapper<GymEmployeeDO>()
                                    .in(GymEmployeeDO::getId, linked)
                                    .eq(GymEmployeeDO::getStatus, "active")
                                    .le(GymEmployeeDO::getHiredOn, today)
                                    .lt(GymEmployeeDO::getHiredOn, req.getEndDate()))
                    .forEach(e -> population.add(e.getId()));
        // Historical event attribution is retained even when a current personnel relationship is
        // gone.
        var amounts = reports.amounts(q);
        var lessons = reports.lessons(q);
        amounts.stream().map(Amount::getId).filter(Objects::nonNull).forEach(population::add);
        lessons.stream().map(Lessons::getId).filter(Objects::nonNull).forEach(population::add);
        BigDecimal total = amounts.stream().map(Amount::getNetAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal attributed =
                amounts.stream()
                        .filter(a -> a.getId() != null)
                        .map(Amount::getNetAmount)
                        .reduce(ZERO, BigDecimal::add);
        long lessonTotal = lessons.stream().mapToLong(Lessons::getLessonCount).sum();
        var buckets = new HashMap<Long, Bucket>();
        boolean byEmployee = "employee".equals(req.getGroupBy());
        if (byEmployee) {
            population.forEach(id -> buckets.put(id, new Bucket(id)));
            var names =
                    population.isEmpty()
                            ? Map.<Long, String>of()
                            : employees.selectBatchIds(population).stream()
                                    .collect(
                                            Collectors.toMap(
                                                    GymEmployeeDO::getId, GymEmployeeDO::getName));
            buckets.forEach((id, value) -> value.name = names.getOrDefault(id, "历史员工"));
        } else {
            selected.forEach(
                    s -> {
                        var v = new Bucket(s.getId());
                        v.name = s.getName();
                        buckets.put(s.getId(), v);
                    });
            q = new Criteria(q.tenant(), q.stores(), q.from(), q.to(), null, false);
            amounts = reports.amounts(q);
            lessons = reports.lessons(q);
        }
        for (var a : amounts) {
            var v = buckets.computeIfAbsent(a.getId(), Bucket::new);
            v.net = a.getNetAmount();
            v.orders = a.getPaidOrderCount();
            if (a.getId() == null) v.name = "未归属员工";
        }
        for (var l : lessons) {
            var v = buckets.computeIfAbsent(l.getId(), Bucket::new);
            v.lessons = l.getLessonCount();
            if (l.getId() == null) v.name = "未归属员工";
        }
        var sorted = new ArrayList<>(buckets.values());
        sorted.sort(
                Comparator.comparing((Bucket v) -> v.id == null)
                        .thenComparing((Bucket v) -> v.net, Comparator.reverseOrder())
                        .thenComparing(v -> v.id, Comparator.nullsLast(Comparator.naturalOrder())));
        var result = new ArrayList<Row>();
        long rank = 0;
        BigDecimal previous = null;
        for (int i = 0; i < sorted.size(); i++) {
            var v = sorted.get(i);
            if (previous == null || v.net.compareTo(previous) != 0) rank = i + 1;
            previous = v.net;
            result.add(
                    new Row(
                            byEmployee
                                    ? (ids.size() == 1 ? ids.getFirst().toString() : null)
                                    : v.id.toString(),
                            byEmployee && v.id != null ? v.id.toString() : null,
                            v.name,
                            self != null || v.id == null ? null : rank,
                            money(v.net),
                            v.orders,
                            v.lessons,
                            average(v.net, days)));
        }
        int offset =
                (int) Math.min(result.size(), ((long) req.getPageNo() - 1) * req.getPageSize());
        return new Result(
                List.copyOf(
                        result.subList(
                                offset, Math.min(result.size(), offset + req.getPageSize()))),
                result.size(),
                new Summary(
                        money(total),
                        population.size(),
                        ids.size(),
                        average(attributed, population.size()),
                        average(total, ids.size()),
                        lessonTotal));
    }
}
