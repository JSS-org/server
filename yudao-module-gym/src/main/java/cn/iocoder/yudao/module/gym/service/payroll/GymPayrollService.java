package cn.iocoder.yudao.module.gym.service.payroll;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.payroll.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.salary.vo.GymSalaryTemplateSaveReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymLessonRecordDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.finance.GymFinancialEventDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.payroll.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.salary.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymLessonRecordMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.finance.GymFinancialEventMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.payroll.*;
import cn.iocoder.yudao.module.gym.dal.mysql.salary.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class GymPayrollService {
    @Resource private GymPayrollMapper gymPayrollMapper;
    @Resource private GymPayrollItemMapper gymPayrollItemMapper;
    @Resource private GymEmployeeMapper gymEmployeeMapper;
    @Resource private GymEmployeeStoreMapper gymEmployeeStoreMapper;
    @Resource private GymSalaryAssignmentMapper gymSalaryAssignmentMapper;
    @Resource private GymSalaryTemplateMapper gymSalaryTemplateMapper;
    @Resource private GymFinancialEventMapper gymFinancialEventMapper;
    @Resource private GymLessonRecordMapper gymLessonRecordMapper;
    @Resource private GymStoreMapper gymStoreMapper;
    @Resource private GymDataScope scope;
    @Resource private PermissionApi permissionApi;
    @Resource private GymBusinessClock gymBusinessClock;
    @Resource private Validator validator;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<GymPayrollRespVO> page(GymBusinessPageReqVO req) {
        var allowed = scope.readableStoreIds();
        Long self = selfEmployee();
        if (req.getStoreId() != null && self == null) scope.check(req.getStoreId());
        var q =
                new LambdaQueryWrapper<GymPayrollDO>()
                        .eq(req.getStoreId() != null, GymPayrollDO::getStoreId, req.getStoreId())
                        .eq(req.getMonth() != null, GymPayrollDO::getPayrollMonth, req.getMonth())
                        .eq(req.getStatus() != null, GymPayrollDO::getStatus, req.getStatus())
                        .orderByDesc(GymPayrollDO::getPayrollMonth)
                        .orderByDesc(GymPayrollDO::getId);
        if (allowed != null)
            q.and(
                    w -> {
                        if (!allowed.isEmpty()) w.in(GymPayrollDO::getStoreId, allowed);
                        if (self != null) {
                            if (!allowed.isEmpty()) w.or();
                            w.apply(
                                    "EXISTS (SELECT 1 FROM gym_payroll_item pi WHERE pi.payroll_id=gym_payroll.id AND pi.deleted=0 AND pi.tenant_id="
                                            + TenantContextHolder.getRequiredTenantId()
                                            + " AND pi.employee_id="
                                            + self
                                            + ")");
                        } else if (allowed.isEmpty()) w.apply("1=0");
                    });
        var page = gymPayrollMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(r -> response(r, false)).toList(), page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymPayrollRespVO get(Long id) {
        return response(require(id, false), true);
    }

    private Long linkedEmployee() {
        var rows =
                gymEmployeeMapper.selectList(
                        new LambdaQueryWrapper<GymEmployeeDO>()
                                .eq(
                                        GymEmployeeDO::getUserId,
                                        SecurityFrameworkUtils.getLoginUserId()));
        return rows.isEmpty() ? null : rows.getFirst().getId();
    }

    private Long selfEmployee() {
        var permission =
                permissionApi.getDeptDataPermission(SecurityFrameworkUtils.getLoginUserId());
        if (permission == null || !Boolean.TRUE.equals(permission.getSelf())) return null;
        return linkedEmployee();
    }

    private boolean storeReadable(Long id) {
        var ids = scope.readableStoreIds();
        return ids == null || ids.contains(id);
    }

    private GymPayrollDO require(Long id, boolean lock) {
        var q = new LambdaQueryWrapper<GymPayrollDO>().eq(GymPayrollDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = gymPayrollMapper.selectOne(q);
        if (row == null) throw exception(PAYROLL_NOT_EXISTS);
        if (!storeReadable(row.getStoreId())) {
            Long self = selfEmployee();
            if (self == null
                    || gymPayrollItemMapper.selectCount(
                                    new LambdaQueryWrapper<GymPayrollItemDO>()
                                            .eq(GymPayrollItemDO::getPayrollId, id)
                                            .eq(GymPayrollItemDO::getEmployeeId, self))
                            == 0) throw exception(STORE_NO_PERMISSION);
        }
        return row;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymPayrollRespVO calculate(GymPayrollCalculateReqVO req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        YearMonth month;
        try {
            month = YearMonth.parse(req.getMonth());
            if (month.getYear() < 1000) throw exception(PAYROLL_MONTH_INVALID);
        } catch (DateTimeException e) {
            throw exception(PAYROLL_MONTH_INVALID);
        }
        scope.check(req.getStoreId());
        var store = gymStoreMapper.selectForUpdate(req.getStoreId());
        if (store == null) throw exception(STORE_NOT_EXISTS);
        var old =
                gymPayrollMapper.selectOne(
                        new LambdaQueryWrapper<GymPayrollDO>()
                                .eq(GymPayrollDO::getStoreId, req.getStoreId())
                                .eq(GymPayrollDO::getPayrollMonth, req.getMonth())
                                .last("FOR UPDATE"));
        if (old != null && "confirmed".equals(old.getStatus())) throw exception(PAYROLL_FROZEN);
        LocalDate from = month.atDay(1), to = month.plusMonths(1).atDay(1);
        var candidates = new TreeSet<Long>();
        for (var link :
                gymEmployeeStoreMapper.selectList(
                        new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                .eq(GymEmployeeStoreDO::getStoreId, req.getStoreId())
                                .lt(GymEmployeeStoreDO::getJoinedOn, to))) {
            var employee = gymEmployeeMapper.selectById(link.getEmployeeId());
            if (employee != null
                    && "active".equals(employee.getStatus())
                    && employee.getHiredOn().isBefore(to)) candidates.add(employee.getId());
        }
        for (var assignment :
                gymSalaryAssignmentMapper.selectList(
                        new LambdaQueryWrapper<GymSalaryAssignmentDO>()
                                .eq(GymSalaryAssignmentDO::getStoreId, req.getStoreId())
                                .lt(GymSalaryAssignmentDO::getEffectiveFrom, to)
                                .and(
                                        w ->
                                                w.isNull(GymSalaryAssignmentDO::getEffectiveTo)
                                                        .or()
                                                        .gt(
                                                                GymSalaryAssignmentDO
                                                                        ::getEffectiveTo,
                                                                from))))
            candidates.add(assignment.getEmployeeId());
        var terms = new ArrayList<CalculationInput>();
        for (Long employeeId : candidates) {
            var employee = gymEmployeeMapper.selectForUpdate(employeeId);
            if (employee == null) throw exception(EMPLOYEE_NOT_EXISTS);
            var overlapping =
                    gymSalaryAssignmentMapper.selectList(
                            new LambdaQueryWrapper<GymSalaryAssignmentDO>()
                                    .eq(GymSalaryAssignmentDO::getEmployeeId, employeeId)
                                    .lt(GymSalaryAssignmentDO::getEffectiveFrom, to)
                                    .and(
                                            w ->
                                                    w.isNull(GymSalaryAssignmentDO::getEffectiveTo)
                                                            .or()
                                                            .gt(
                                                                    GymSalaryAssignmentDO
                                                                            ::getEffectiveTo,
                                                                    from))
                                    .last("FOR UPDATE"));
            var full =
                    overlapping.stream()
                            .filter(
                                    a ->
                                            !a.getEffectiveFrom().isAfter(from)
                                                    && (a.getEffectiveTo() == null
                                                            || !a.getEffectiveTo().isBefore(to)))
                            .toList();
            if (overlapping.size() != 1 || full.size() != 1)
                throw exception(PAYROLL_ASSIGNMENT_INVALID, employee.getName());
            var assignment = full.getFirst();
            if (!req.getStoreId().equals(assignment.getStoreId()))
                continue; // The employee has one explicit paying store, even with multiple
            // appointments.
            if (!"active".equals(employee.getStatus()) && assignment.getEffectiveTo() == null)
                throw exception(PAYROLL_INACTIVE_INTERVAL_REQUIRED, employee.getName());
            if (employee.getHiredOn().isAfter(from))
                throw exception(PAYROLL_ASSIGNMENT_INVALID, employee.getName());
            var template = gymSalaryTemplateMapper.selectById(assignment.getTemplateId());
            if (template == null
                    || !"published".equals(template.getStatus())
                    || template.getEffectiveFrom().isAfter(from)
                    || (template.getEffectiveTo() != null
                            && template.getEffectiveTo().isBefore(to)))
                throw exception(PAYROLL_ASSIGNMENT_INVALID, employee.getName());
            terms.add(new CalculationInput(employee, assignment, template));
        }
        // Capture each immutable source stream once; all employees use these same input sets.
        Instant starts = from.atStartOfDay(GymDateRange.ZONE).toInstant(),
                ends = to.atStartOfDay(GymDateRange.ZONE).toInstant();
        var employeeIds = terms.stream().map(t -> t.employee().getId()).toList();
        List<GymFinancialEventDO> events =
                employeeIds.isEmpty()
                        ? List.of()
                        : gymFinancialEventMapper.selectList(
                                new LambdaQueryWrapper<GymFinancialEventDO>()
                                        .in(GymFinancialEventDO::getEmployeeId, employeeIds)
                                        .ge(GymFinancialEventDO::getOccurredAt, starts)
                                        .lt(GymFinancialEventDO::getOccurredAt, ends)
                                        .orderByAsc(GymFinancialEventDO::getId));
        List<GymLessonRecordDO> lessons =
                employeeIds.isEmpty()
                        ? List.of()
                        : gymLessonRecordMapper.selectList(
                                new LambdaQueryWrapper<GymLessonRecordDO>()
                                        .in(GymLessonRecordDO::getCoachId, employeeIds)
                                        .eq(GymLessonRecordDO::getStatus, "confirmed")
                                        .ge(GymLessonRecordDO::getConfirmedAt, starts)
                                        .lt(GymLessonRecordDO::getConfirmedAt, ends)
                                        .orderByAsc(GymLessonRecordDO::getId));
        var sourceStores = new HashSet<Long>();
        events.forEach(e -> sourceStores.add(e.getStoreId()));
        lessons.forEach(l -> sourceStores.add(l.getStoreId()));
        var permitted = scope.readableStoreIds();
        if (permitted != null && !permitted.containsAll(sourceStores))
            throw exception(PAYROLL_SOURCE_NO_PERMISSION);
        var items = new ArrayList<GymPayrollItemDO>();
        for (var term : terms)
            items.add(
                    calculateItem(
                            term.employee(),
                            term.assignment(),
                            term.template(),
                            from,
                            to,
                            events,
                            lessons));
        // No previous item or total is replaced until every employee validates and computes
        // successfully.
        var payroll = old == null ? new GymPayrollDO() : old;
        payroll.setStoreId(req.getStoreId());
        payroll.setStoreName(store.getName());
        payroll.setPayrollMonth(req.getMonth());
        payroll.setEmployeeCount(items.size());
        payroll.setNetAmount(
                items.stream().map(GymPayrollItemDO::getNetAmount).reduce(ZERO, BigDecimal::add));
        payroll.setStatus("calculated");
        payroll.setCalculatedAt(gymBusinessClock.now());
        if (old == null) gymPayrollMapper.insert(payroll);
        else gymPayrollMapper.updateById(payroll);
        gymPayrollItemMapper.delete(
                new LambdaQueryWrapper<GymPayrollItemDO>()
                        .eq(GymPayrollItemDO::getPayrollId, payroll.getId()));
        for (var item : items) {
            item.setPayrollId(payroll.getId());
            gymPayrollItemMapper.insert(item);
        }
        return response(payroll, true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymPayrollRespVO recalculate(Long id) {
        var existing = gymPayrollMapper.selectById(id);
        if (existing == null) throw exception(PAYROLL_NOT_EXISTS);
        scope.check(existing.getStoreId());
        var req = new GymPayrollCalculateReqVO();
        req.setStoreId(existing.getStoreId());
        req.setMonth(existing.getPayrollMonth());
        return calculate(req);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymPayrollRespVO confirm(Long id) {
        var initial = gymPayrollMapper.selectById(id);
        if (initial == null) throw exception(PAYROLL_NOT_EXISTS);
        scope.check(initial.getStoreId());
        gymStoreMapper.selectForUpdate(initial.getStoreId());
        var row = require(id, true);
        if (!"confirmed".equals(row.getStatus())) {
            var start = YearMonth.parse(row.getPayrollMonth()).atDay(1);
            var end = start.plusMonths(1);
            var items =
                    gymPayrollItemMapper.selectList(
                            new LambdaQueryWrapper<GymPayrollItemDO>()
                                    .eq(GymPayrollItemDO::getPayrollId, id)
                                    .orderByAsc(GymPayrollItemDO::getEmployeeId));
            for (var item : items) {
                var employee = gymEmployeeMapper.selectForUpdate(item.getEmployeeId());
                var assignment =
                        gymSalaryAssignmentMapper.selectForUpdate(item.getSalaryAssignmentId());
                if (employee == null
                        || assignment == null
                        || assignment.getEffectiveFrom().isAfter(start)
                        || (assignment.getEffectiveTo() != null
                                && assignment.getEffectiveTo().isBefore(end))
                        || !assignment.getStoreId().equals(row.getStoreId())
                        || employee.getHiredOn().isAfter(start))
                    throw exception(PAYROLL_ASSIGNMENT_INVALID, item.getEmployeeName());
                if (!"active".equals(employee.getStatus()) && assignment.getEffectiveTo() == null)
                    throw exception(PAYROLL_INACTIVE_INTERVAL_REQUIRED, employee.getName());
            }
            row.setStatus("confirmed");
            row.setConfirmedAt(gymBusinessClock.now());
            row.setConfirmedBy(SecurityFrameworkUtils.getLoginUserId());
            gymPayrollMapper.updateById(row);
        }
        return response(row, true);
    }

    private record CalculationInput(
            GymEmployeeDO employee,
            GymSalaryAssignmentDO assignment,
            GymSalaryTemplateDO template) {}

    private GymPayrollItemDO calculateItem(
            GymEmployeeDO employee,
            GymSalaryAssignmentDO assignment,
            GymSalaryTemplateDO template,
            LocalDate from,
            LocalDate to,
            List<GymFinancialEventDO> allEvents,
            List<GymLessonRecordDO> allLessons) {
        var events =
                allEvents.stream().filter(e -> employee.getId().equals(e.getEmployeeId())).toList();
        var lessons =
                allLessons.stream().filter(l -> employee.getId().equals(l.getCoachId())).toList();
        var components = new ArrayList<GymPayrollRespVO.Component>();
        var base = component("base", "底薪", "base", money(template.getBaseSalary()));
        base.setBasisAmount(template.getBaseSalary());
        base.setSourceSnapshot(
                Map.of(
                        "assignmentId",
                        assignment.getId().toString(),
                        "templateId",
                        template.getId().toString()));
        components.add(base);
        var totals = new HashMap<String, BigDecimal>();
        for (String type : List.of("card_sales", "pt_sales", "lessons", "allowance", "deduction"))
            totals.put(type, ZERO);
        var rules =
                JsonUtils.parseArray(template.getRules(), GymSalaryTemplateSaveReqVO.Rule.class);
        int index = 0;
        for (var rule : rules) {
            BigDecimal value;
            var source = new LinkedHashMap<String, Object>();
            source.put("rule", rule);
            var c =
                    component(
                            "rule-" + (++index),
                            label(rule.getComponent()),
                            rule.getComponent(),
                            ZERO);
            switch (rule.getComponent()) {
                case "card_sales", "pt_sales" -> {
                    var rows =
                            events.stream()
                                    .filter(
                                            e ->
                                                    ("card_sales".equals(rule.getComponent())
                                                                    ? "card"
                                                                    : "private")
                                                            .equals(e.getProductType()))
                                    .toList();
                    BigDecimal net =
                            rows.stream()
                                    .map(
                                            e ->
                                                    "refund".equals(e.getEventType())
                                                            ? e.getAmount().negate()
                                                            : e.getAmount())
                                    .reduce(ZERO, BigDecimal::add);
                    BigDecimal lower = rule.getLowerBound() == null ? ZERO : rule.getLowerBound(),
                            basis = net.abs();
                    if (rule.getUpperBound() != null) basis = basis.min(rule.getUpperBound());
                    basis = basis.subtract(lower).max(ZERO);
                    if (net.signum() < 0) basis = basis.negate();
                    value = money(basis.multiply(rule.getRate()));
                    c.setBasisAmount(basis);
                    c.setRate(rule.getRate());
                    source.put("netAmount", net);
                    source.put("events", rows.stream().map(this::eventSnapshot).toList());
                    source.put("refundPolicy", "本月净额为负时按相同阶梯反向计算，不将退款归零");
                }
                case "lessons" -> {
                    long quantity =
                            lessons.stream().mapToLong(GymLessonRecordDO::getSessionUnits).sum();
                    value = money(rule.getUnitAmount().multiply(BigDecimal.valueOf(quantity)));
                    c.setBasisQuantity(quantity);
                    source.put("unitAmount", rule.getUnitAmount());
                    source.put(
                            "lessons",
                            lessons.stream()
                                    .map(
                                            l ->
                                                    Map.of(
                                                            "id",
                                                            l.getId().toString(),
                                                            "bookingId",
                                                            l.getBookingId().toString(),
                                                            "sessionUnits",
                                                            l.getSessionUnits(),
                                                            "storeId",
                                                            l.getStoreId().toString(),
                                                            "confirmedAt",
                                                            l.getConfirmedAt()))
                                    .toList());
                }
                case "allowance", "deduction" -> value = money(rule.getFixedAmount());
                default -> throw exception(SALARY_RULE_INVALID);
            }
            totals.merge(rule.getComponent(), value, BigDecimal::add);
            c.setAmount("deduction".equals(rule.getComponent()) ? value.negate() : value);
            c.setSourceSnapshot(source);
            components.add(c);
        }
        var item = new GymPayrollItemDO();
        item.setSalaryAssignmentId(assignment.getId());
        item.setEmployeeId(employee.getId());
        item.setEmployeeName(employee.getName());
        item.setBaseSalary(money(template.getBaseSalary()));
        item.setCardCommission(totals.get("card_sales"));
        item.setPtCommission(totals.get("pt_sales"));
        item.setLessonCommission(totals.get("lessons"));
        item.setAllowances(totals.get("allowance"));
        item.setDeductions(totals.get("deduction"));
        item.setGrossAmount(
                item.getBaseSalary()
                        .add(item.getCardCommission())
                        .add(item.getPtCommission())
                        .add(item.getLessonCommission())
                        .add(item.getAllowances()));
        item.setNetAmount(item.getGrossAmount().subtract(item.getDeductions()));
        item.setComponents(JsonUtils.toJsonString(components));
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("basis", "整月底薪和固定加扣；本人各任职门店实际支付/退款净额与已确认消课汇总至唯一发薪门店");
        snapshot.put(
                "template",
                Map.of(
                        "id",
                        template.getId().toString(),
                        "familyId",
                        template.getFamilyId().toString(),
                        "name",
                        template.getName(),
                        "version",
                        template.getVersion(),
                        "baseSalary",
                        template.getBaseSalary(),
                        "rules",
                        rules));
        snapshot.put(
                "employment",
                Map.of(
                        "employeeId",
                        employee.getId().toString(),
                        "name",
                        employee.getName(),
                        "hiredOn",
                        employee.getHiredOn()));
        var assignmentSnapshot = new LinkedHashMap<String, Object>();
        assignmentSnapshot.put("id", assignment.getId().toString());
        assignmentSnapshot.put("storeId", assignment.getStoreId().toString());
        assignmentSnapshot.put("effectiveFrom", assignment.getEffectiveFrom());
        assignmentSnapshot.put("effectiveTo", assignment.getEffectiveTo());
        snapshot.put("assignment", assignmentSnapshot);
        snapshot.put("assignmentId", assignment.getId().toString());
        snapshot.put("from", from);
        snapshot.put("toExclusive", to);
        item.setCalculationSnapshot(JsonUtils.toJsonString(snapshot));
        return item;
    }

    private Map<String, Object> eventSnapshot(GymFinancialEventDO e) {
        return Map.of(
                "storeId",
                e.getStoreId().toString(),
                "id",
                e.getId().toString(),
                "eventType",
                e.getEventType(),
                "productType",
                e.getProductType(),
                "amount",
                e.getAmount(),
                "occurredAt",
                e.getOccurredAt(),
                "sourceType",
                e.getSourceType(),
                "sourceId",
                e.getSourceId());
    }

    private GymPayrollRespVO.Component component(
            String id, String name, String type, BigDecimal amount) {
        var c = new GymPayrollRespVO.Component();
        c.setId(id);
        c.setLabel(name);
        c.setComponent(type);
        c.setAmount(amount);
        return c;
    }

    private String label(String component) {
        return switch (component) {
            case "card_sales" -> "会员卡销售提成";
            case "pt_sales" -> "私教销售提成";
            case "lessons" -> "消课提成";
            case "allowance" -> "固定加项";
            default -> "固定扣项";
        };
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private GymPayrollRespVO response(GymPayrollDO row, boolean detail) {
        var result = BeanUtils.toBean(row, GymPayrollRespVO.class);
        result.setMonth(row.getPayrollMonth());
        boolean whole = storeReadable(row.getStoreId());
        Long self = whole ? null : selfEmployee();
        var items =
                gymPayrollItemMapper.selectList(
                        new LambdaQueryWrapper<GymPayrollItemDO>()
                                .eq(GymPayrollItemDO::getPayrollId, row.getId())
                                .eq(!whole, GymPayrollItemDO::getEmployeeId, self)
                                .orderByAsc(GymPayrollItemDO::getEmployeeId));
        if (!whole) {
            result.setEmployeeCount(items.size());
            result.setNetAmount(
                    items.stream()
                            .map(GymPayrollItemDO::getNetAmount)
                            .reduce(ZERO, BigDecimal::add));
        }
        Long sourceOwner = linkedEmployee();
        if (detail)
            result.setItems(
                    items.stream()
                            .map(
                                    item -> {
                                        var v = new GymPayrollRespVO.Item();
                                        org.springframework.beans.BeanUtils.copyProperties(
                                                item,
                                                v,
                                                "id",
                                                "employeeId",
                                                "components",
                                                "calculationSnapshot");
                                        v.setId(item.getId().toString());
                                        v.setEmployeeId(item.getEmployeeId().toString());
                                        v.setComponents(
                                                JsonUtils.parseArray(
                                                        item.getComponents(),
                                                        GymPayrollRespVO.Component.class));
                                        v.setCalculationSnapshot(
                                                JsonUtils.parseObject(
                                                        item.getCalculationSnapshot(), Map.class));
                                        maskSources(v, sourceOwner);
                                        return v;
                                    })
                            .toList());
        return result;
    }

    private void maskSources(GymPayrollRespVO.Item item, Long sourceOwner) {
        var allowed = scope.readableStoreIds();
        if (allowed == null
                || (sourceOwner != null && sourceOwner.toString().equals(item.getEmployeeId())))
            return;
        for (var component : item.getComponents())
            for (String key : List.of("events", "lessons")) {
                var snapshot = component.getSourceSnapshot();
                if (snapshot == null || !(snapshot.get(key) instanceof List<?> list)) continue;
                var visible =
                        list.stream()
                                .filter(
                                        entry ->
                                                entry instanceof Map<?, ?> data
                                                        && data.get("storeId") != null
                                                        && allowed.contains(
                                                                Long.valueOf(
                                                                        data.get("storeId")
                                                                                .toString())))
                                .toList();
                if (visible.size() != list.size()) {
                    snapshot.put(key, visible);
                    snapshot.put("hiddenSourceCount", list.size() - visible.size());
                    snapshot.put("sourceVisibility", "部分跨店来源明细按权限隐藏，计薪汇总保持不变");
                }
            }
    }
}
