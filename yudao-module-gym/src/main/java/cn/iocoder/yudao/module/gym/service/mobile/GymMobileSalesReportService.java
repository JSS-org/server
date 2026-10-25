package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.BUSINESS_DATE_INVALID;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileSalesReportVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymOrderItemDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileSalesReportAggregate.*;
import cn.iocoder.yudao.module.gym.dal.mysql.sales.GymOrderItemMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;

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
public class GymMobileSalesReportService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private GymStoreMapper stores;
    @Resource private GymOrderItemMapper items;
    @Resource private GymMobileSalesReportMapper reports;
    @Resource private Validator validator;

    private static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String average(BigDecimal v, long n) {
        return n == 0 ? "0.00" : money(v.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result sales(Query req) {
        access.requireAdmin();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        long days = ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate());
        if (req.getStartDate().getYear() < 1000
                || req.getEndDate().getYear() > 9999
                || days < 1
                || days > 366) throw exception(BUSINESS_DATE_INVALID);
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        var allowed = scope.readableStoreIds();
        if (allowed != null && allowed.isEmpty()) return empty();
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
        if (ids.isEmpty()) return empty();
        var q =
                new Criteria(
                        TenantContextHolder.getRequiredTenantId(),
                        ids,
                        req.getStartDate().atStartOfDay(GymDateRange.ZONE).toInstant(),
                        req.getEndDate().atStartOfDay(GymDateRange.ZONE).toInstant(),
                        "course".equals(req.getProductType()) ? "private" : req.getProductType(),
                        req.getEmployeeId());
        var totals = reports.totals(q);
        long total = reports.count(q);
        var page =
                reports.rows(
                        q, req.getPageSize(), ((long) req.getPageNo() - 1) * req.getPageSize());
        var orderIds =
                page.stream()
                        .filter(r -> r.getOrderNo() != null)
                        .map(Row::getOrderId)
                        .distinct()
                        .toList();
        var names =
                orderIds.isEmpty()
                        ? Map.<Long, List<GymOrderItemDO>>of()
                        : items
                                .selectList(
                                        new LambdaQueryWrapper<GymOrderItemDO>()
                                                .in(GymOrderItemDO::getOrderId, orderIds)
                                                .orderByAsc(GymOrderItemDO::getId))
                                .stream()
                                .collect(Collectors.groupingBy(GymOrderItemDO::getOrderId));
        var result =
                page.stream()
                        .map(
                                r -> {
                                    String name =
                                            r.getOrderNo() == null
                                                    ? null
                                                    : names
                                                            .getOrDefault(r.getOrderId(), List.of())
                                                            .stream()
                                                            .filter(
                                                                    i ->
                                                                            r.getProductType()
                                                                                    .equals(
                                                                                            i
                                                                                                    .getProductType()))
                                                            .map(
                                                                    GymOrderItemDO
                                                                            ::getProductNameSnapshot)
                                                            .filter(Objects::nonNull)
                                                            .distinct()
                                                            .collect(Collectors.joining(" / "));
                                    if (name != null && name.isEmpty()) name = null;
                                    return new SaleRow(
                                            r.getId().toString(),
                                            r.getOrderNo(),
                                            r.getStoreId().toString(),
                                            r.getEmployeeId() == null
                                                    ? null
                                                    : r.getEmployeeId().toString(),
                                            "private".equals(r.getProductType())
                                                    ? "course"
                                                    : "card",
                                            name,
                                            money(r.getPaidAmount()),
                                            money(r.getRefundedAmount()),
                                            money(
                                                    r.getPaidAmount()
                                                            .subtract(r.getRefundedAmount())),
                                            r.getPaidAt() == null
                                                    ? null
                                                    : r.getPaidAt()
                                                            .atZone(GymDateRange.ZONE)
                                                            .toOffsetDateTime()
                                                            .toString());
                                })
                        .toList();
        return new Result(
                result,
                total,
                new Summary(
                        money(totals.getPaidAmount()),
                        money(totals.getRefundedAmount()),
                        money(totals.getPaidAmount().subtract(totals.getRefundedAmount())),
                        totals.getPaidOrderCount(),
                        money(totals.getCardAmount()),
                        totals.getCardOrderCount(),
                        average(totals.getCardAmount(), totals.getCardOrderCount()),
                        money(totals.getCourseAmount()),
                        totals.getCourseOrderCount(),
                        average(totals.getCourseAmount(), totals.getCourseOrderCount())));
    }

    private Result empty() {
        return new Result(
                List.of(),
                0,
                new Summary("0.00", "0.00", "0.00", 0, "0.00", 0, "0.00", "0.00", 0, "0.00"));
    }
}
