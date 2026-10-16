package cn.iocoder.yudao.module.gym.service.report;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.BUSINESS_DATE_INVALID;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportRespVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.approval.GymApprovalDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.finance.GymFinancialEventDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.approval.GymApprovalMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.card.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.finance.GymFinancialEventMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class GymReportService {
    @Resource private GymStoreMapper gymStoreMapper;
    @Resource private GymEmployeeMapper gymEmployeeMapper;
    @Resource private GymEmployeeStoreMapper gymEmployeeStoreMapper;
    @Resource private GymMemberMapper gymMemberMapper;
    @Resource private GymMemberStoreMapper gymMemberStoreMapper;
    @Resource private GymMemberCardMapper gymMemberCardMapper;
    @Resource private GymMemberCardStoreMapper gymMemberCardStoreMapper;
    @Resource private GymCoursePackMapper gymCoursePackMapper;
    @Resource private GymCheckinMapper gymCheckinMapper;
    @Resource private GymFinancialEventMapper gymFinancialEventMapper;
    @Resource private cn.iocoder.yudao.module.gym.dal.mysql.report.GymCardReportMapper cardReports;
    @Resource private GymCardTemplateMapper gymCardTemplateMapper;
    @Resource private GymApprovalMapper gymApprovalMapper;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessClock clock;
    @Resource private PermissionApi permissionApi;
    @Resource private Validator validator;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private void validate(GymReportReqVO req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private List<GymStoreDO> stores(GymReportReqVO req) {
        validate(req);
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        var allowed = scope.readableStoreIds();
        if (allowed != null && allowed.isEmpty()) return List.of();
        return gymStoreMapper.selectList(
                new LambdaQueryWrapper<GymStoreDO>()
                        .eq(req.getStoreId() != null, GymStoreDO::getId, req.getStoreId())
                        .in(allowed != null, GymStoreDO::getId, allowed)
                        .orderByAsc(GymStoreDO::getId));
    }

    private List<Long> storeIds(List<GymStoreDO> stores) {
        return stores.stream().map(GymStoreDO::getId).toList();
    }

    private GymDateRange range(GymReportReqVO req, boolean bounded) {
        var parsed = GymDateRange.of(req);
        var end = parsed.to();
        var start = parsed.from();
        if (bounded || req.getDays() != null) {
            if (end == null)
                end =
                        clock.now()
                                .atZone(GymDateRange.ZONE)
                                .toLocalDate()
                                .plusDays(1)
                                .atStartOfDay(GymDateRange.ZONE)
                                .toInstant();
            if (start == null)
                start =
                        end.atZone(GymDateRange.ZONE)
                                .minusDays(req.getDays() == null ? 7 : req.getDays())
                                .toInstant();
        }
        if ((start != null && start.atZone(GymDateRange.ZONE).getYear() < 1000)
                || (end != null && end.atZone(GymDateRange.ZONE).getYear() > 9999)
                || (start != null
                        && end != null
                        && (!end.isAfter(start)
                                || Duration.between(start, end).compareTo(Duration.ofDays(366))
                                        > 0))) throw exception(BUSINESS_DATE_INVALID);
        return new GymDateRange(start, end);
    }

    private List<GymFinancialEventDO> events(List<Long> ids, GymDateRange range) {
        if (ids.isEmpty()) return List.of();
        return gymFinancialEventMapper.selectList(
                new LambdaQueryWrapper<GymFinancialEventDO>()
                        .in(GymFinancialEventDO::getStoreId, ids)
                        .ge(range.from() != null, GymFinancialEventDO::getOccurredAt, range.from())
                        .lt(range.to() != null, GymFinancialEventDO::getOccurredAt, range.to())
                        .orderByAsc(GymFinancialEventDO::getId));
    }

    private BigDecimal net(List<GymFinancialEventDO> events, String type) {
        return events.stream()
                .filter(e -> type == null || type.equals(e.getProductType()))
                .map(
                        e ->
                                "refund".equals(e.getEventType())
                                        ? e.getAmount().negate()
                                        : e.getAmount())
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private long orders(List<GymFinancialEventDO> events) {
        return events.stream()
                .filter(e -> "payment".equals(e.getEventType()))
                .map(GymFinancialEventDO::getBusinessOrderId)
                .distinct()
                .count();
    }

    private BigDecimal average(BigDecimal amount, long count) {
        return count == 0
                ? ZERO
                : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private <T> List<T> slice(List<T> values, GymReportReqVO req) {
        int start = (int) Math.min(values.size(), ((long) req.getPageNo() - 1) * req.getPageSize());
        return values.subList(start, Math.min(values.size(), start + req.getPageSize()));
    }

    public Page<Performance> performance(GymReportReqVO req) {
        var stores = stores(req);
        var ids = storeIds(stores);
        var range = range(req, false);
        var events = events(ids, range);
        var links =
                ids.isEmpty()
                        ? List.<GymEmployeeStoreDO>of()
                        : gymEmployeeStoreMapper.selectList(
                                new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                        .in(GymEmployeeStoreDO::getStoreId, ids));
        var employeeIds = new HashSet<Long>();
        links.forEach(l -> employeeIds.add(l.getEmployeeId()));
        events.stream()
                .map(GymFinancialEventDO::getEmployeeId)
                .filter(Objects::nonNull)
                .forEach(employeeIds::add);
        var employees =
                employeeIds.isEmpty()
                        ? Map.<Long, GymEmployeeDO>of()
                        : gymEmployeeMapper.selectBatchIds(employeeIds).stream()
                                .collect(
                                        Collectors.toMap(
                                                GymEmployeeDO::getId, Function.identity()));
        var result = new ArrayList<Performance>();
        if ("employee".equals(req.getGroupBy())) {
            for (Long id : new TreeSet<>(employeeIds)) {
                var employee = employees.get(id);
                var rows = events.stream().filter(e -> id.equals(e.getEmployeeId())).toList();
                if (employee != null && "active".equals(employee.getStatus()) || !rows.isEmpty())
                    result.add(
                            performanceRow(
                                    id.toString(),
                                    employee == null ? "历史员工" : employee.getName(),
                                    rows,
                                    1));
            }
            var unassigned = events.stream().filter(e -> e.getEmployeeId() == null).toList();
            if (!unassigned.isEmpty())
                result.add(performanceRow("unassigned", "未归属员工", unassigned, 0));
        } else
            for (var store : stores) {
                var active =
                        links.stream()
                                .filter(l -> store.getId().equals(l.getStoreId()))
                                .map(GymEmployeeStoreDO::getEmployeeId)
                                .distinct()
                                .filter(
                                        id ->
                                                employees.containsKey(id)
                                                        && "active"
                                                                .equals(
                                                                        employees
                                                                                .get(id)
                                                                                .getStatus()))
                                .count();
                result.add(
                        performanceRow(
                                store.getId().toString(),
                                store.getName(),
                                events.stream()
                                        .filter(e -> store.getId().equals(e.getStoreId()))
                                        .toList(),
                                active));
            }
        return new Page<>(
                slice(result, req), result.size(), new Summary(net(events, null), orders(events)));
    }

    private Performance performanceRow(
            String id, String name, List<GymFinancialEventDO> events, long employees) {
        var r = new Performance();
        r.setId(id);
        r.setName(name);
        r.setNetAmount(net(events, null));
        r.setCardAmount(net(events, "card"));
        r.setPtAmount(net(events, "private"));
        r.setOrderCount(orders(events));
        r.setEmployeeCount(employees);
        r.setAverageEmployeeAmount(average(r.getNetAmount(), employees));
        r.setAverageOrderAmount(average(r.getNetAmount(), r.getOrderCount()));
        return r;
    }

    private List<GymMemberStoreDO> links(List<Long> ids) {
        return ids.isEmpty()
                ? List.of()
                : gymMemberStoreMapper.selectList(
                        new LambdaQueryWrapper<GymMemberStoreDO>()
                                .in(GymMemberStoreDO::getStoreId, ids));
    }

    private Map<Long, GymMemberDO> members(List<GymMemberStoreDO> links) {
        var ids = links.stream().map(GymMemberStoreDO::getMemberId).distinct().toList();
        return ids.isEmpty()
                ? Map.of()
                : gymMemberMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(GymMemberDO::getId, Function.identity()));
    }

    private boolean within(Instant value, GymDateRange range) {
        return value != null
                && (range.from() == null || !value.isBefore(range.from()))
                && (range.to() == null || value.isBefore(range.to()));
    }

    public Page<Members> members(GymReportReqVO req) {
        var stores = stores(req);
        var range = range(req, false);
        long total = stores.size();
        stores = slice(stores, req);
        var ids = storeIds(stores);
        var links = links(ids);
        var members = members(links);
        var cardLinks =
                ids.isEmpty()
                        ? List.<GymMemberCardStoreDO>of()
                        : gymMemberCardStoreMapper.selectList(
                                new LambdaQueryWrapper<GymMemberCardStoreDO>()
                                        .in(GymMemberCardStoreDO::getStoreId, ids));
        var cardIds = cardLinks.stream().map(GymMemberCardStoreDO::getCardId).distinct().toList();
        Instant now = clock.now(), limit = now.plus(Duration.ofDays(30));
        var cards =
                cardIds.isEmpty()
                        ? Map.<Long, GymMemberCardDO>of()
                        : gymMemberCardMapper
                                .selectList(
                                        new LambdaQueryWrapper<GymMemberCardDO>()
                                                .in(GymMemberCardDO::getId, cardIds)
                                                .eq(GymMemberCardDO::getStatus, "active")
                                                .gt(GymMemberCardDO::getExpiresAt, now)
                                                .le(GymMemberCardDO::getExpiresAt, limit))
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                GymMemberCardDO::getId, Function.identity()));
        var packs =
                ids.isEmpty()
                        ? List.<GymCoursePackDO>of()
                        : gymCoursePackMapper.selectList(
                                new LambdaQueryWrapper<GymCoursePackDO>()
                                        .in(GymCoursePackDO::getStoreId, ids)
                                        .eq(GymCoursePackDO::getStatus, "active")
                                        .gt(GymCoursePackDO::getExpiresAt, now)
                                        .le(GymCoursePackDO::getExpiresAt, limit)
                                        .gt(GymCoursePackDO::getRemainingSessions, 0));
        var result = new ArrayList<Members>();
        for (var store : stores) {
            var owned =
                    links.stream()
                            .filter(l -> store.getId().equals(l.getStoreId()))
                            .map(GymMemberStoreDO::getMemberId)
                            .distinct()
                            .map(members::get)
                            .filter(Objects::nonNull)
                            .toList();
            var ownedIds = owned.stream().map(GymMemberDO::getId).collect(Collectors.toSet());
            long expiringCards =
                    cardLinks.stream()
                            .filter(l -> store.getId().equals(l.getStoreId()))
                            .map(l -> cards.get(l.getCardId()))
                            .filter(Objects::nonNull)
                            .map(GymMemberCardDO::getMemberId)
                            .filter(ownedIds::contains)
                            .distinct()
                            .count();
            long expiringCourses =
                    packs.stream()
                            .filter(
                                    p ->
                                            store.getId().equals(p.getStoreId())
                                                    && ownedIds.contains(p.getMemberId()))
                            .map(GymCoursePackDO::getMemberId)
                            .distinct()
                            .count();
            result.add(
                    new Members(
                            store.getId().toString(),
                            store.getName(),
                            owned.size(),
                            owned.stream().filter(m -> within(m.getRegisteredAt(), range)).count(),
                            owned.stream().filter(m -> "lead".equals(m.getCustomerStage())).count(),
                            expiringCards,
                            expiringCourses));
        }
        return new Page<>(result, total, null);
    }

    public Dashboard dashboard(GymReportReqVO req) {
        var stores = stores(req);
        var ids = storeIds(stores);
        if (req.getDays() == null
                && req.getFrom() == null
                && req.getTo() == null
                && req.getDate() == null
                && req.getMonth() == null) req.setDays(7);
        var range = range(req, true);
        var events = events(ids, range);
        var checkins =
                ids.isEmpty()
                        ? List.<GymCheckinDO>of()
                        : gymCheckinMapper.selectList(
                                new LambdaQueryWrapper<GymCheckinDO>()
                                        .in(GymCheckinDO::getStoreId, ids)
                                        .eq(GymCheckinDO::getStatus, "succeeded")
                                        .ge(GymCheckinDO::getEnteredAt, range.from())
                                        .lt(GymCheckinDO::getEnteredAt, range.to()));
        var members = members(links(ids)).values();
        var net = net(events, null);
        var trend = new ArrayList<Trend>();
        for (LocalDate day = range.from().atZone(GymDateRange.ZONE).toLocalDate();
                day.isBefore(
                        range.to()
                                .atZone(GymDateRange.ZONE)
                                .toLocalDate()
                                .plusDays(
                                        range.to()
                                                        .atZone(GymDateRange.ZONE)
                                                        .toLocalTime()
                                                        .equals(LocalTime.MIDNIGHT)
                                                ? 0
                                                : 1));
                day = day.plusDays(1)) {
            var date = day;
            trend.add(
                    new Trend(
                            day.toString(),
                            net(
                                    events.stream()
                                            .filter(
                                                    e ->
                                                            e.getOccurredAt()
                                                                    .atZone(GymDateRange.ZONE)
                                                                    .toLocalDate()
                                                                    .equals(date))
                                            .toList(),
                                    null),
                            checkins.stream()
                                    .filter(
                                            c ->
                                                    c.getEnteredAt()
                                                            .atZone(GymDateRange.ZONE)
                                                            .toLocalDate()
                                                            .equals(date))
                                    .count()));
        }
        long active = members.stream().filter(m -> "active".equals(m.getStatus())).count(),
                leads =
                        members.stream()
                                .filter(
                                        m ->
                                                "active".equals(m.getStatus())
                                                        && "lead".equals(m.getCustomerStage()))
                                .count();
        var metrics =
                new Metrics(
                        net,
                        checkins.size(),
                        members.stream().filter(m -> within(m.getRegisteredAt(), range)).count(),
                        active);
        return new Dashboard(
                range.from().atZone(GymDateRange.ZONE).toLocalDate().toString(),
                range.to().minusNanos(1).atZone(GymDateRange.ZONE).toLocalDate().toString(),
                metrics,
                trend,
                List.of(
                        new Segment("会员", active - leads),
                        new Segment("潜客", leads),
                        new Segment("停用", members.size() - active)),
                pending(ids, stores));
    }

    private List<PendingApproval> pending(List<Long> ids, List<GymStoreDO> stores) {
        Long user = SecurityFrameworkUtils.getLoginUserId();
        if (ids.isEmpty()
                || !permissionApi.hasAnyPermissions(user, "gym:approvals:read")
                || !permissionApi.hasAnyPermissions(user, "gym:approvals:decide")) return List.of();
        long tenant = TenantContextHolder.getRequiredTenantId();
        String task =
                "SELECT 1 FROM gym_approval_task t JOIN gym_employee e ON e.id=t.approver_employee_id AND e.user_id=t.approver_user_id AND e.status='active' AND e.deleted=0 AND e.tenant_id={0} WHERE t.approval_id=gym_approval.id AND t.step_no=gym_approval.current_step AND t.status='pending' AND t.approver_user_id={1} AND t.deleted=0 AND t.tenant_id={0}";
        var rows =
                gymApprovalMapper.selectList(
                        new LambdaQueryWrapper<GymApprovalDO>()
                                .in(GymApprovalDO::getStoreId, ids)
                                .eq(GymApprovalDO::getStatus, "pending")
                                .exists(task, tenant, user)
                                .orderByAsc(GymApprovalDO::getSubmittedAt)
                                .orderByAsc(GymApprovalDO::getId)
                                .last("LIMIT 10"));
        var names =
                stores.stream().collect(Collectors.toMap(GymStoreDO::getId, GymStoreDO::getName));
        return rows.stream()
                .map(
                        r ->
                                new PendingApproval(
                                        r.getId().toString(),
                                        r.getTitle(),
                                        r.getBusinessType(),
                                        names.get(r.getStoreId()),
                                        r.getApplicantName(),
                                        r.getSubmittedAt()))
                .toList();
    }

    public Page<MemberCards> memberCards(GymReportReqVO req) {
        var ids = storeIds(stores(req));
        var period = range(req, false);
        if (ids.isEmpty()) return new Page<>(List.of(), 0, new Summary(ZERO, 0));
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var rows = new TreeMap<Long, MemberCards>();
        for (var template :
                gymCardTemplateMapper.selectList(
                        new LambdaQueryWrapper<GymCardTemplateDO>()
                                .in(GymCardTemplateDO::getStatus, List.of("published", "inactive"))
                                .orderByAsc(GymCardTemplateDO::getId))) {
            if (!"all".equals(template.getAccessScope())
                    && cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(
                                    template.getStoreIds(), Long.class)
                            .stream()
                            .noneMatch(ids::contains)) continue;
            var row = new MemberCards();
            row.setId(template.getId().toString());
            row.setName(template.getName());
            row.setVersion(template.getVersion());
            rows.put(template.getId(), row);
        }
        for (var aggregate : cardReports.sold(tenant, ids, period.from(), period.to()))
            cardReportRow(rows, aggregate).setSoldCount(aggregate.getCount());
        for (var aggregate : cardReports.net(tenant, ids, period.from(), period.to()))
            cardReportRow(rows, aggregate).setNetAmount(aggregate.getAmount().setScale(2));
        for (var aggregate : cardReports.holders(tenant, ids, clock.now()))
            cardReportRow(rows, aggregate).setHolderCount(aggregate.getCount());
        for (var aggregate : cardReports.usages(tenant, ids, period.from(), period.to()))
            cardReportRow(rows, aggregate).setUsageCount(aggregate.getCount());
        var totals = cardReports.totals(tenant, ids, period.from(), period.to());
        var result = new ArrayList<>(rows.values());
        BigDecimal unattributed =
                totals.getAmount()
                        .subtract(
                                result.stream()
                                        .map(MemberCards::getNetAmount)
                                        .reduce(ZERO, BigDecimal::add))
                        .setScale(2);
        if (unattributed.signum() != 0) {
            var row = new MemberCards();
            row.setId("unattributed");
            row.setName("未归属卡种");
            row.setNetAmount(unattributed);
            result.add(row);
        }
        return new Page<>(
                slice(result, req),
                result.size(),
                new Summary(totals.getAmount().setScale(2), totals.getCount()));
    }

    private MemberCards cardReportRow(
            Map<Long, MemberCards> rows,
            cn.iocoder.yudao.module.gym.dal.mysql.report.GymCardReportAggregate source) {
        return rows.computeIfAbsent(
                source.getTemplateId(),
                id -> {
                    var row = new MemberCards();
                    row.setId(id.toString());
                    row.setName(source.getName());
                    row.setVersion(source.getVersion());
                    if (row.getVersion() == null && source.getTemplateSnapshot() != null) {
                        var snapshot =
                                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                                        source.getTemplateSnapshot(), Map.class);
                        if (snapshot.get("version") != null)
                            row.setVersion(Integer.valueOf(snapshot.get("version").toString()));
                    }
                    return row;
                });
    }
}
