package cn.iocoder.yudao.module.gym.service.employee;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.employee.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

@Service
public class GymEmployeeService {
    @Resource private GymEmployeeMapper employeeMapper;
    @Resource private GymEmployeeStoreMapper assignmentMapper;
    @Resource private GymStoreService storeService;
    @Resource private GymDataScope scope;
    @Resource private AdminUserService userService;
    @Resource private Validator validator;
    @Resource private cn.iocoder.yudao.module.gym.service.salary.GymSalaryService salaries;

    public PageResult<GymEmployeeRespVO> page(GymPageReqVO req) {
        Set<Long> ids = scope.readableStoreIds();
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        if (ids != null && ids.isEmpty()) return new PageResult<>(Collections.emptyList(), 0L);
        LambdaQueryWrapper<GymEmployeeDO> query = new LambdaQueryWrapper<>();
        // 子查询显式绑定租户，不能依赖主表拦截器替子查询补齐范围。
        String relation = "SELECT 1 FROM gym_employee_store es WHERE es.employee_id = gym_employee.id AND es.deleted = 0 AND es.tenant_id = {0}";
        if (req.getStoreId() != null) {
            query.exists(relation + " AND es.store_id = {1}", TenantContextHolder.getRequiredTenantId(), req.getStoreId());
        } else if (ids != null) {
            String allowed = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            query.exists(relation + " AND es.store_id IN (" + allowed + ")", TenantContextHolder.getRequiredTenantId());
        }
        query.eq(req.getStatus() != null, GymEmployeeDO::getStatus, req.getStatus());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(q -> q.like(GymEmployeeDO::getName, req.getKeyword()).or().like(GymEmployeeDO::getEmployeeNo, req.getKeyword())
                    .or().like(GymEmployeeDO::getPhone, req.getKeyword()));
        }
        query.orderByDesc(GymEmployeeDO::getId);
        PageResult<GymEmployeeDO> result = employeeMapper.selectPage(req, query);
        return new PageResult<>(result.getList().stream().map(e -> response(e, assignments(e.getId()), ids)).toList(), result.getTotal());
    }
    public GymEmployeeRespVO get(Long id) {
        GymEmployeeDO employee = requireEmployee(id, false);
        List<GymEmployeeStoreDO> assignments = assignments(id);
        Set<Long> ids = scope.readableStoreIds();
        if (ids != null && assignments.stream().noneMatch(a -> ids.contains(a.getStoreId()))) throw exception(STORE_NO_PERMISSION);
        return response(employee, assignments, ids);
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public GymEmployeeRespVO create(GymEmployeeSaveReqVO req) {
        if (req.getStatus() == null) req.setStatus("active");
        validate(req);
        validateAssignments(req);
        validateUser(req, null);
        GymEmployeeDO employee = BeanUtils.toBean(req, GymEmployeeDO.class);
        try { employeeMapper.insert(employee); } catch (DuplicateKeyException e) { throw exception(EMPLOYEE_NO_DUPLICATE); }
        saveAssignments(employee.getId(), req.getStoreAssignments());
        if (req.getSalaryAssignment() != null) salaries.assign(employee.getId(), req.getSalaryAssignment());
        return response(employee, assignments(employee.getId()), scope.readableStoreIds());
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public GymEmployeeRespVO patch(Long id, GymEmployeeSaveReqVO patch) {
        GymEmployeeDO old = requireEmployee(id, true);
        List<GymEmployeeStoreDO> current = assignments(id);
        // 写共享档案必须覆盖全部原任职门店，防止借本店权限修改/移除跨店关系。
        scope.checkAll(current.stream().map(GymEmployeeStoreDO::getStoreId).toList());
        if (current.isEmpty()) scope.requireHeadquarters();
        if (patch.getEmployeeNo() != null && !patch.getEmployeeNo().equals(old.getEmployeeNo())) throw exception(EMPLOYEE_NO_IMMUTABLE);
        GymEmployeeSaveReqVO merged = BeanUtils.toBean(old, GymEmployeeSaveReqVO.class);
        merged.setStoreAssignments(BeanUtils.toBean(current, GymEmployeeSaveReqVO.StoreAssignment.class));
        GymPatchUtils.merge(patch, merged);
        validate(merged);
        validateAssignments(merged);
        validateUser(merged, old.getUserId());
        GymEmployeeDO update = BeanUtils.toBean(merged, GymEmployeeDO.class); update.setId(id);
        try { employeeMapper.updateById(update); } catch (DuplicateKeyException e) { throw exception(EMPLOYEE_NO_DUPLICATE); }
        // 不触碰未提交的任职关系，避免普通资料编辑改变关系行 ID。
        if (patch.getStoreAssignments() != null) saveAssignments(id, merged.getStoreAssignments());
        if (patch.getSalaryAssignment() != null) salaries.assign(id, patch.getSalaryAssignment());
        return response(update, assignments(id), scope.readableStoreIds());
    }
    private GymEmployeeDO requireEmployee(Long id, boolean lock) {
        GymEmployeeDO employee = lock ? employeeMapper.selectForUpdate(id) : employeeMapper.selectById(id);
        if (employee == null) throw exception(EMPLOYEE_NOT_EXISTS);
        return employee;
    }
    private void validate(GymEmployeeSaveReqVO req) {
        var violations = validator.validate(req);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    }
    private void validateAssignments(GymEmployeeSaveReqVO req) {
        Set<Long> seen = new HashSet<>(); int primary = 0;
        for (GymEmployeeSaveReqVO.StoreAssignment a : req.getStoreAssignments()) {
            storeService.requireStore(a.getStoreId(), false);
            if (!seen.add(a.getStoreId()) || a.getJoinedOn().isBefore(req.getHiredOn())) throw exception(EMPLOYEE_ASSIGNMENT_INVALID);
            if (Boolean.TRUE.equals(a.getIsPrimary())) primary++;
        }
        if (primary != 1) throw exception(EMPLOYEE_ASSIGNMENT_INVALID);
    }
    private void validateUser(GymEmployeeSaveReqVO req, Long previousUserId) {
        if (req.getUserId() == null) return;
        if (!req.getUserId().equals(previousUserId)) scope.checkAccountLinkPermission();
        AdminUserDO user = userService.getUser(req.getUserId());
        Long primary = req.getStoreAssignments().stream().filter(a -> Boolean.TRUE.equals(a.getIsPrimary()))
                .findFirst().orElseThrow().getStoreId();
        if (user == null || !Integer.valueOf(0).equals(user.getStatus()) || !primary.equals(user.getDeptId())) throw exception(EMPLOYEE_USER_INVALID);
    }
    private List<GymEmployeeStoreDO> assignments(Long id) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<GymEmployeeStoreDO>()
                .eq(GymEmployeeStoreDO::getEmployeeId, id).orderByDesc(GymEmployeeStoreDO::getIsPrimary).orderByAsc(GymEmployeeStoreDO::getId));
    }
    private void saveAssignments(Long id, List<GymEmployeeSaveReqVO.StoreAssignment> assignments) {
        assignmentMapper.deleteRelations(id);
        for (GymEmployeeSaveReqVO.StoreAssignment a : assignments) {
            GymEmployeeStoreDO row = BeanUtils.toBean(a, GymEmployeeStoreDO.class); row.setEmployeeId(id); assignmentMapper.insert(row);
        }
    }
    private GymEmployeeRespVO response(GymEmployeeDO employee, List<GymEmployeeStoreDO> assignments, Set<Long> scopeIds) {
        GymEmployeeRespVO result = BeanUtils.toBean(employee, GymEmployeeRespVO.class);
        result.setId(employee.getId().toString());
        result.setUserId(employee.getUserId() == null ? null : employee.getUserId().toString());
        if (employee.getUserId() != null) {
            AdminUserDO user = userService.getUser(employee.getUserId());
            if (user != null) result.setUsername(user.getUsername());
        }
        List<GymEmployeeRespVO.StoreAssignment> visible = assignments.stream()
                .filter(a -> scopeIds == null || scopeIds.contains(a.getStoreId()))
                .map(a -> { var item = BeanUtils.toBean(a, GymEmployeeRespVO.StoreAssignment.class);
                    item.setStoreId(a.getStoreId().toString()); return item; }).toList();
        result.setStoreAssignments(visible);
        result.setStoreIds(visible.stream().map(GymEmployeeRespVO.StoreAssignment::getStoreId).toList());
        return result;
    }
}
