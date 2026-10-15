package cn.iocoder.yudao.module.gym.service.salary;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gym.controller.admin.salary.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.salary.*;
import cn.iocoder.yudao.module.gym.dal.mysql.salary.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeStoreDO;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

@Service
public class GymSalaryService {
    @Resource private cn.iocoder.yudao.module.gym.dal.mysql.payroll.GymPayrollItemMapper gymPayrollItemMapper;
    @Resource private cn.iocoder.yudao.module.gym.dal.mysql.payroll.GymPayrollMapper gymPayrollMapper;
    @Resource private GymSalaryTemplateMapper gymSalaryTemplateMapper;
    @Resource private GymSalaryAssignmentMapper gymSalaryAssignmentMapper;
    @Resource private GymEmployeeMapper gymEmployeeMapper;
    @Resource private GymEmployeeStoreMapper gymEmployeeStoreMapper;
    @Resource private GymStoreService stores;
    @Resource private GymDataScope scope;
    @Resource private PermissionApi permissions;
    @Resource private Validator validator;

    public PageResult<GymSalaryTemplateRespVO> templates(GymSalaryPageReqVO req) {
        var query = new LambdaQueryWrapper<GymSalaryTemplateDO>()
                .eq(req.getStatus() != null, GymSalaryTemplateDO::getStatus, req.getStatus())
                .like(req.getKeyword() != null && !req.getKeyword().isBlank(), GymSalaryTemplateDO::getName, req.getKeyword())
                .orderByDesc(GymSalaryTemplateDO::getId);
        var page = gymSalaryTemplateMapper.selectPage(req, query);
        return new PageResult<>(page.getList().stream().map(this::templateResponse).toList(), page.getTotal());
    }
    public GymSalaryTemplateRespVO template(Long id) { return templateResponse(requireTemplate(id, false)); }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public GymSalaryTemplateRespVO create(GymSalaryTemplateSaveReqVO req) {
        scope.requireHeadquarters();
        validate(req);
        if (req.getStatus() != null && !"draft".equals(req.getStatus())) throw exception(SALARY_TEMPLATE_STATE_INVALID);
        GymSalaryTemplateDO row = templateRow(req);
        row.setStatus("draft"); row.setVersion(1);
        gymSalaryTemplateMapper.insert(row);
        row.setFamilyId(row.getId()); gymSalaryTemplateMapper.updateById(row);
        return templateResponse(row);
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public GymSalaryTemplateRespVO patch(Long id, GymSalaryTemplateSaveReqVO patch) {
        scope.requireHeadquarters();
        GymSalaryTemplateDO row = requireTemplate(id, true);
        if (!"draft".equals(row.getStatus())) throw exception(SALARY_TEMPLATE_IMMUTABLE);
        var req = new GymSalaryTemplateSaveReqVO();
        org.springframework.beans.BeanUtils.copyProperties(row, req, "rules");
        req.setRules(JsonUtils.parseArray(row.getRules(), GymSalaryTemplateSaveReqVO.Rule.class));
        if (patch.getName() != null) req.setName(patch.getName());
        if (patch.getBaseSalary() != null) req.setBaseSalary(patch.getBaseSalary());
        if (patch.getEffectiveFrom() != null) req.setEffectiveFrom(patch.getEffectiveFrom());
        // Distinguish omitted end date from an explicit JSON null.
        if (patch.isEffectiveToPresent()) req.setEffectiveTo(patch.getEffectiveTo());
        if (patch.getRules() != null) req.setRules(patch.getRules());
        if (patch.getStatus() != null) req.setStatus(patch.getStatus());
        validate(req);
        var updated = templateRow(req); updated.setId(id); updated.setFamilyId(row.getFamilyId()); updated.setVersion(row.getVersion());
        gymSalaryTemplateMapper.updateById(updated);
        return templateResponse(updated);
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public GymSalaryTemplateRespVO version(Long id, GymSalaryTemplateSaveReqVO req) {
        scope.requireHeadquarters();
        GymSalaryTemplateDO source = requireTemplate(id, false);
        // Every version creation locks the immutable family root, serializing version numbers.
        requireTemplate(source.getFamilyId(), true);
        validate(req);
        if (req.getStatus() != null && !"draft".equals(req.getStatus())) throw exception(SALARY_TEMPLATE_STATE_INVALID);
        var latest = gymSalaryTemplateMapper.selectList(new LambdaQueryWrapper<GymSalaryTemplateDO>()
                .eq(GymSalaryTemplateDO::getFamilyId, source.getFamilyId()).orderByDesc(GymSalaryTemplateDO::getVersion).last("FOR UPDATE")).getFirst();
        var row = templateRow(req); row.setFamilyId(source.getFamilyId()); row.setVersion(latest.getVersion() + 1); row.setStatus("draft");
        gymSalaryTemplateMapper.insert(row);
        return templateResponse(row);
    }
    private GymSalaryTemplateDO requireTemplate(Long id, boolean lock) {
        var row = lock ? gymSalaryTemplateMapper.selectForUpdate(id) : gymSalaryTemplateMapper.selectById(id);
        if (row == null) throw exception(SALARY_TEMPLATE_NOT_EXISTS);
        return row;
    }
    private void validate(GymSalaryTemplateSaveReqVO req) {
        validateBean(req);
        validInterval(req.getEffectiveFrom(), req.getEffectiveTo());
        for (var rule : req.getRules()) {
            switch (rule.getComponent()) {
                case "card_sales", "pt_sales" -> {
                    if (!"rate".equals(rule.getCalculationType()) || rule.getRate() == null
                            || rule.getFixedAmount() != null || rule.getUnitAmount() != null) throw exception(SALARY_RULE_INVALID);
                    if (rule.getUpperBound() != null && rule.getUpperBound().compareTo(
                            rule.getLowerBound() == null ? BigDecimal.ZERO : rule.getLowerBound()) <= 0) throw exception(SALARY_RULE_INVALID);
                }
                case "lessons" -> {
                    if (!"per_unit".equals(rule.getCalculationType()) || rule.getUnitAmount() == null
                            || rule.getRate() != null || rule.getFixedAmount() != null || rule.getLowerBound() != null || rule.getUpperBound() != null) throw exception(SALARY_RULE_INVALID);
                }
                default -> {
                    if (!"fixed".equals(rule.getCalculationType()) || rule.getFixedAmount() == null
                            || rule.getRate() != null || rule.getUnitAmount() != null || rule.getLowerBound() != null || rule.getUpperBound() != null) throw exception(SALARY_RULE_INVALID);
                }
            }
        }
        // Sales ranges form tiers, not additive duplicates; reject overlapping ranges.
        for (int i = 0; i < req.getRules().size(); i++) {
            var a = req.getRules().get(i);
            if (!Set.of("card_sales", "pt_sales").contains(a.getComponent())) continue;
            for (int j = i + 1; j < req.getRules().size(); j++) {
                var b = req.getRules().get(j);
                if (a.getComponent().equals(b.getComponent())
                        && (a.getUpperBound() == null || value(b.getLowerBound()).compareTo(a.getUpperBound()) < 0)
                        && (b.getUpperBound() == null || value(a.getLowerBound()).compareTo(b.getUpperBound()) < 0)) throw exception(SALARY_RULE_INVALID);
            }
        }
    }
    private BigDecimal value(BigDecimal number) { return number == null ? BigDecimal.ZERO : number; }
    private void validateBean(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }
    private void validInterval(LocalDate from, LocalDate to) {
        if (to != null && !to.isAfter(from)) throw exception(SALARY_INTERVAL_INVALID);
    }
    private GymSalaryTemplateDO templateRow(GymSalaryTemplateSaveReqVO req) {
        var row = new GymSalaryTemplateDO();
        org.springframework.beans.BeanUtils.copyProperties(req, row, "rules");
        row.setRules(JsonUtils.toJsonString(req.getRules()));
        return row;
    }
    private GymSalaryTemplateRespVO templateResponse(GymSalaryTemplateDO row) {
        var result = new GymSalaryTemplateRespVO();
        org.springframework.beans.BeanUtils.copyProperties(row, result, "rules", "id", "familyId");
        result.setId(row.getId().toString()); result.setFamilyId(row.getFamilyId().toString());
        result.setRules(JsonUtils.parseArray(row.getRules(), GymSalaryTemplateSaveReqVO.Rule.class));
        return result;
    }

    /** Called inside the employee transaction after locking the employee and validating its assignments. */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public void assign(Long employeeId, GymSalaryAssignmentReqVO req) {
        if (!permissions.hasAnyPermissions(SecurityFrameworkUtils.getLoginUserId(), "gym:payroll:templates")) {
            throw new org.springframework.security.access.AccessDeniedException("工资配置需要 gym:payroll:templates 权限");
        }
        validateBean(req);
        scope.check(req.getStoreId());
        var employee = gymEmployeeMapper.selectForUpdate(employeeId);
        if (employee == null) throw exception(EMPLOYEE_NOT_EXISTS);
        var relation = gymEmployeeStoreMapper.selectList(new LambdaQueryWrapper<GymEmployeeStoreDO>()
                .eq(GymEmployeeStoreDO::getEmployeeId, employeeId).eq(GymEmployeeStoreDO::getStoreId, req.getStoreId()).last("FOR UPDATE"));
        if (relation.isEmpty() || req.getEffectiveFrom().isBefore(employee.getHiredOn())
                || req.getEffectiveFrom().isBefore(relation.getFirst().getJoinedOn())) throw exception(SALARY_ASSIGNMENT_INVALID);
        validInterval(req.getEffectiveFrom(), req.getEffectiveTo());
        var template = requireTemplate(req.getTemplateId(), false);
        if (!"published".equals(template.getStatus()) || req.getEffectiveFrom().isBefore(template.getEffectiveFrom())
                || (template.getEffectiveTo() != null && (req.getEffectiveTo() == null || req.getEffectiveTo().isAfter(template.getEffectiveTo())))) {
            throw exception(SALARY_ASSIGNMENT_INVALID);
        }
        var history = gymSalaryAssignmentMapper.selectList(new LambdaQueryWrapper<GymSalaryAssignmentDO>()
                .eq(GymSalaryAssignmentDO::getEmployeeId, employeeId).orderByAsc(GymSalaryAssignmentDO::getEffectiveFrom).last("FOR UPDATE"));
        for (var previous : history) {
            if (Objects.equals(previous.getStoreId(), req.getStoreId()) && Objects.equals(previous.getTemplateId(), req.getTemplateId())
                    && Objects.equals(previous.getEffectiveFrom(), req.getEffectiveFrom()) && Objects.equals(previous.getEffectiveTo(), req.getEffectiveTo())) return;
        }
        GymSalaryAssignmentDO close = null;
        for (var previous : history) {
            if ((previous.getEffectiveTo() == null || req.getEffectiveFrom().isBefore(previous.getEffectiveTo()))
                    && (req.getEffectiveTo() == null || previous.getEffectiveFrom().isBefore(req.getEffectiveTo()))) {
                if (previous.getEffectiveTo() == null && previous.getEffectiveFrom().isBefore(req.getEffectiveFrom()) && close == null) close = previous;
                else throw exception(SALARY_ASSIGNMENT_OVERLAP);
            }
        }
        if (close != null) { scope.check(close.getStoreId()); checkConfirmedPeriod(close.getId(),req.getEffectiveFrom()); close.setEffectiveTo(req.getEffectiveFrom()); gymSalaryAssignmentMapper.updateById(close); }
        var row = BeanUtils.toBean(req, GymSalaryAssignmentDO.class); row.setEmployeeId(employeeId);
        gymSalaryAssignmentMapper.insert(row);
    }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public GymSalaryAssignmentRespVO createAssignment(GymSalaryAssignmentReqVO req) {
        if (req.getEmployeeId() == null) throw exception(SALARY_ASSIGNMENT_INVALID);
        assign(req.getEmployeeId(), req);
        return assignmentResponse(gymSalaryAssignmentMapper.selectList(new LambdaQueryWrapper<GymSalaryAssignmentDO>()
                .eq(GymSalaryAssignmentDO::getEmployeeId, req.getEmployeeId())
                .eq(GymSalaryAssignmentDO::getEffectiveFrom, req.getEffectiveFrom()).last("FOR UPDATE")).getFirst());
    }
    public GymSalaryAssignmentRespVO assignment(Long id) { return assignmentResponse(requireAssignment(id)); }
    private GymSalaryAssignmentDO requireAssignment(Long id) {
        var row = gymSalaryAssignmentMapper.selectById(id);
        if (row == null) throw exception(SALARY_ASSIGNMENT_NOT_EXISTS);
        scope.check(row.getStoreId());
        return row;
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public GymSalaryAssignmentRespVO patchAssignment(Long id, GymSalaryAssignmentReqVO patch) {
        var first = requireAssignment(id);
        // Use the same employee lock as assignment creation; don't reverse the lock order.
        gymEmployeeMapper.selectForUpdate(first.getEmployeeId());
        var row = gymSalaryAssignmentMapper.selectForUpdate(id);
        if (row == null) throw exception(SALARY_ASSIGNMENT_NOT_EXISTS);
        scope.check(row.getStoreId());
        if ((patch.getEmployeeId() != null && !patch.getEmployeeId().equals(row.getEmployeeId()))
                || (patch.getStoreId() != null && !patch.getStoreId().equals(row.getStoreId()))
                || (patch.getTemplateId() != null && !patch.getTemplateId().equals(row.getTemplateId()))
                || (patch.getEffectiveFrom() != null && !patch.getEffectiveFrom().equals(row.getEffectiveFrom()))) throw exception(SALARY_ASSIGNMENT_INVALID);
        var to = patch.isEffectiveToPresent() ? patch.getEffectiveTo() : row.getEffectiveTo();
        validInterval(row.getEffectiveFrom(), to);
        var template = requireTemplate(row.getTemplateId(), false);
        if (template.getEffectiveTo() != null && (to == null || to.isAfter(template.getEffectiveTo()))) throw exception(SALARY_ASSIGNMENT_INVALID);
        var history = gymSalaryAssignmentMapper.selectList(new LambdaQueryWrapper<GymSalaryAssignmentDO>()
                .eq(GymSalaryAssignmentDO::getEmployeeId, row.getEmployeeId()).ne(GymSalaryAssignmentDO::getId, id).last("FOR UPDATE"));
        for (var other : history) {
            if ((other.getEffectiveTo() == null || row.getEffectiveFrom().isBefore(other.getEffectiveTo()))
                    && (to == null || other.getEffectiveFrom().isBefore(to))) throw exception(SALARY_ASSIGNMENT_OVERLAP);
        }
        checkConfirmedPeriod(id,to); row.setEffectiveTo(to); gymSalaryAssignmentMapper.updateById(row);
        return assignmentResponse(row);
    }

    public PageResult<GymSalaryAssignmentRespVO> assignments(GymSalaryPageReqVO req) {
        var ids = scope.readableStoreIds();
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        if (ids != null && ids.isEmpty()) return new PageResult<>(List.of(), 0L);
        var query = new LambdaQueryWrapper<GymSalaryAssignmentDO>()
                .eq(req.getStoreId() != null, GymSalaryAssignmentDO::getStoreId, req.getStoreId())
                .eq(req.getEmployeeId() != null, GymSalaryAssignmentDO::getEmployeeId, req.getEmployeeId())
                .eq(req.getTemplateId() != null, GymSalaryAssignmentDO::getTemplateId, req.getTemplateId())
                .in(ids != null, GymSalaryAssignmentDO::getStoreId, ids)
                .orderByDesc(GymSalaryAssignmentDO::getEffectiveFrom).orderByDesc(GymSalaryAssignmentDO::getId);
        var page = gymSalaryAssignmentMapper.selectPage(req, query);
        return new PageResult<>(page.getList().stream().map(this::assignmentResponse).toList(), page.getTotal());
    }
    private GymSalaryAssignmentRespVO assignmentResponse(GymSalaryAssignmentDO row) {
        var result = BeanUtils.toBean(row, GymSalaryAssignmentRespVO.class);
        var employee = gymEmployeeMapper.selectById(row.getEmployeeId());
        result.setEmployeeName(employee == null ? null : employee.getName());
        result.setStoreName(stores.get(row.getStoreId()).getName());
        var template = requireTemplate(row.getTemplateId(), false);
        result.setTemplateName(template.getName()); result.setTemplateVersion(template.getVersion());
        return result;
    }

    private void checkConfirmedPeriod(Long assignmentId,java.time.LocalDate to) {
        if(to==null)return;
        for(var item:gymPayrollItemMapper.selectList(new LambdaQueryWrapper<cn.iocoder.yudao.module.gym.dal.dataobject.payroll.GymPayrollItemDO>()
                .eq(cn.iocoder.yudao.module.gym.dal.dataobject.payroll.GymPayrollItemDO::getSalaryAssignmentId,assignmentId))) {
            var payroll=gymPayrollMapper.selectById(item.getPayrollId());
            if(payroll!=null&&"confirmed".equals(payroll.getStatus())&&to.isBefore(java.time.YearMonth.parse(payroll.getPayrollMonth()).plusMonths(1).atDay(1)))throw exception(PAYROLL_ASSIGNMENT_FROZEN);
        }
    }
}
