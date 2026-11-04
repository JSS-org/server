package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.approval.vo.GymApprovalDecisionReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileApprovalsVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.gym.dal.mysql.approval.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.approval.*;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMobileApprovalsService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private PermissionApi permissions;
    @Resource private SecurityFrameworkService security;
    @Resource private GymApprovalMapper approvals;
    @Resource private GymApprovalTaskMapper tasks;
    @Resource private GymApprovalTemplateMapper templates;
    @Resource private GymApprovalTemplateService templateService;
    @Resource private GymEmployeeMapper employees;
    @Resource private GymStoreMapper stores;
    @Resource private AdminUserService users;
    @Resource private GymMemberCoachService memberCoaches;
    @Resource private GymApprovalService business;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private Validator validator;

    private void valid(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private String broad() {
        var ids = scope.readableStoreIds();
        return ids == null
                ? "1=1"
                : ids.isEmpty()
                        ? "1=0"
                        : "gym_approval.store_id IN ("
                                + ids.stream()
                                        .sorted()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(","))
                                + ")";
    }

    private String task(String condition) {
        return "SELECT 1 FROM gym_approval_task t JOIN gym_employee e ON e.id=t.approver_employee_id AND e.tenant_id={0} AND e.deleted=0 AND e.status='active' AND e.user_id=t.approver_user_id WHERE t.tenant_id={0} AND t.deleted=0 AND t.approval_id=gym_approval.id AND t.approver_user_id={1} "
                + condition;
    }

    private LambdaQueryWrapper<GymApprovalDO> readable(Long user) {
        return new LambdaQueryWrapper<GymApprovalDO>()
                .and(
                        q ->
                                q.apply(broad())
                                        .or()
                                        .eq(GymApprovalDO::getApplicantUserId, user)
                                        .or()
                                        .exists(
                                                task(""),
                                                TenantContextHolder.getRequiredTenantId(),
                                                user));
    }

    private GymApprovalDO readable(Long id, Long user) {
        if (id == null || id <= 0) throw exception(APPROVAL_NOT_EXISTS);
        var r = approvals.selectOne(readable(user).eq(GymApprovalDO::getId, id));
        if (r == null) throw exception(APPROVAL_NOT_EXISTS);
        return r;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Approval> page(Query req) {
        Long user = access.requireAdmin();
        valid(req);
        var q = new LambdaQueryWrapper<GymApprovalDO>();
        switch (req.getView()) {
            case "mine" -> q.eq(GymApprovalDO::getApplicantUserId, user);
            case "pending" -> {
                if (!security.hasPermission("gym:approvals:decide"))
                    return new PageResult<>(List.of(), 0L);
                q.apply(broad())
                        .eq(GymApprovalDO::getStatus, "pending")
                        .exists(
                                task(
                                        "AND t.status='pending' AND t.step_no=gym_approval.current_step"),
                                TenantContextHolder.getRequiredTenantId(),
                                user);
            }
            case "processed" ->
                    q.exists(
                            task("AND t.status IN ('approved','rejected')"),
                            TenantContextHolder.getRequiredTenantId(),
                            user);
            default -> throw exception(APPROVAL_REQUEST_INVALID);
        }
        q.eq(req.getStoreId() != null, GymApprovalDO::getStoreId, req.getStoreId())
                .eq(req.getStatus() != null, GymApprovalDO::getStatus, req.getStatus())
                .orderByDesc(GymApprovalDO::getSubmittedAt, GymApprovalDO::getId);
        var page = approvals.selectPage(req, q);
        return new PageResult<>(project(page.getList()), page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Approval get(Long id) {
        return project(List.of(readable(id, access.requireAdmin()))).getFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Options options(OptionsQuery req) {
        access.requireAdmin();
        valid(req);
        var broad = scope.readableStoreIds();
        boolean personal = broad != null && !broad.contains(req.getStoreId());
        if (personal) {
            access.employee();
            memberCoaches.checkListStore(req.getStoreId());
            if (!"price_change".equals(req.getBusinessType())) throw exception(STORE_NO_PERMISSION);
        } else scope.check(req.getStoreId());
        var store = stores.selectById(req.getStoreId());
        if (store == null || !"active".equals(store.getStatus()))
            throw exception(APPROVAL_REQUEST_INVALID);
        var candidates =
                templates.selectList(
                        new LambdaQueryWrapper<GymApprovalTemplateDO>()
                                .eq(GymApprovalTemplateDO::getStatus, "published")
                                .eq(
                                        personal,
                                        GymApprovalTemplateDO::getBusinessType,
                                        "price_change")
                                .orderByDesc(GymApprovalTemplateDO::getId));
        var usable = candidates.stream().filter(t -> actionable(t, req.getStoreId())).toList();
        return new Options(
                usable.stream()
                        .filter(t -> req.getBusinessType().equals(t.getBusinessType()))
                        .map(t -> new Template(t.getId().toString(), t.getName(), t.getVersion()))
                        .toList(),
                usable.stream()
                        .map(GymApprovalTemplateDO::getBusinessType)
                        .distinct()
                        .sorted()
                        .toList());
    }

    private boolean actionable(GymApprovalTemplateDO template, Long store) {
        var steps = templateService.steps(template);
        if (steps == null || steps.isEmpty()) return false;
        var seen = new HashSet<Long>();
        int number = 1;
        for (var step : steps) {
            if (step.getStepNo() != number++) return false;
            var employee = employees.selectById(step.getApproverEmployeeId());
            if (employee == null
                    || !"active".equals(employee.getStatus())
                    || employee.getUserId() == null
                    || !seen.add(employee.getUserId())) return false;
            var account =
                    DataPermissionUtils.executeIgnore(() -> users.getUser(employee.getUserId()));
            if (account == null
                    || !TenantContextHolder.getRequiredTenantId().equals(account.getTenantId())
                    || !Integer.valueOf(0).equals(account.getStatus())) return false;
            var permission = permissions.getDeptDataPermission(employee.getUserId());
            if (permission == null
                    || !(Boolean.TRUE.equals(permission.getAll())
                            || permission.getDeptIds() != null
                                    && permission.getDeptIds().contains(store))
                    || !permissions.hasAnyPermissions(employee.getUserId(), "gym:approvals:decide"))
                return false;
        }
        return true;
    }

    private void decisionActor(GymApprovalDO row, Long user) {
        scope.check(row.getStoreId());
        if (approvals.selectCount(
                        new LambdaQueryWrapper<GymApprovalDO>()
                                .eq(GymApprovalDO::getId, row.getId())
                                .exists(task(""), TenantContextHolder.getRequiredTenantId(), user))
                == 0) throw exception(APPROVAL_DECISION_FORBIDDEN);
    }

    private record DecisionPayload(
            Long approvalId, String decision, String comment, Integer expectedVersion) {}

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Approval decide(Long id, Decision req, String key) {
        Long user = access.requireAdmin();
        valid(req);
        String comment = req.getComment() == null ? null : req.getComment().trim();
        if ("reject".equals(req.getDecision()) && (comment == null || comment.isBlank()))
            throw exception(APPROVAL_REQUEST_INVALID);
        decisionActor(readable(id, user), user);
        return idempotency.execute(
                "approval-decision",
                key,
                new DecisionPayload(id, req.getDecision(), comment, req.getExpectedVersion()),
                Approval.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    var row = approvals.selectForUpdate(id);
                    if (row == null) throw exception(APPROVAL_NOT_EXISTS);
                    decisionActor(row, user);
                    if (!Objects.equals(row.getVersion(), req.getExpectedVersion()))
                        throw exception(APPROVAL_VERSION_CONFLICT);
                    if (!"pending".equals(row.getStatus())
                            || approvals.selectCount(
                                            new LambdaQueryWrapper<GymApprovalDO>()
                                                    .eq(GymApprovalDO::getId, id)
                                                    .exists(
                                                            task(
                                                                    "AND t.status='pending' AND t.step_no=gym_approval.current_step"),
                                                            TenantContextHolder
                                                                    .getRequiredTenantId(),
                                                            user))
                                    == 0) throw exception(APPROVAL_DECISION_INVALID);
                    var original = new GymApprovalDecisionReqVO();
                    original.setDecision(req.getDecision());
                    original.setComment(comment);
                    business.decide(id, original);
                    return project(List.of(approvals.selectById(id))).getFirst();
                });
    }

    private List<Approval> project(List<GymApprovalDO> rows) {
        if (rows.isEmpty()) return List.of();
        var ids = rows.stream().map(GymApprovalDO::getId).toList();
        var steps =
                tasks
                        .selectList(
                                new LambdaQueryWrapper<GymApprovalTaskDO>()
                                        .in(GymApprovalTaskDO::getApprovalId, ids)
                                        .orderByAsc(GymApprovalTaskDO::getStepNo))
                        .stream()
                        .collect(Collectors.groupingBy(GymApprovalTaskDO::getApprovalId));
        return rows.stream()
                .map(
                        r ->
                                new Approval(
                                        r.getId().toString(),
                                        r.getBusinessType(),
                                        r.getStoreId().toString(),
                                        r.getApplicantUserId().toString(),
                                        r.getStatus(),
                                        r.getCurrentStep(),
                                        r.getVersion(),
                                        summary(r),
                                        steps.getOrDefault(r.getId(), List.of()).stream()
                                                .map(
                                                        t ->
                                                                new Step(
                                                                        t.getStepNo(),
                                                                        t.getApproverUserId()
                                                                                .toString(),
                                                                        t.getStatus(),
                                                                        t.getComment(),
                                                                        time(t.getDecidedAt())))
                                                .toList(),
                                        time(r.getSubmittedAt())))
                .toList();
    }

    private Map<String, Object> summary(GymApprovalDO row) {
        var raw = JsonUtils.parseObject(row.getRequestSnapshot(), Map.class);
        var result = new LinkedHashMap<String, Object>();
        List<String> fields =
                switch (row.getBusinessType()) {
                    case "card_opening" ->
                            List.of(
                                    "reason",
                                    "memberId",
                                    "memberName",
                                    "cardTemplateId",
                                    "cardName",
                                    "cardType",
                                    "productType",
                                    "productId",
                                    "productVersion",
                                    "courseName",
                                    "coachId",
                                    "sessionCount",
                                    "durationMinutes",
                                    "amount",
                                    "sourceId",
                                    "sourceType",
                                    "orderNo");
                    case "price_change" ->
                            List.of(
                                    "reason",
                                    "priceScope",
                                    "productId",
                                    "productVersion",
                                    "memberId",
                                    "coachId",
                                    "sessionCount",
                                    "durationMinutes",
                                    "sourceId",
                                    "sourceType",
                                    "courseName",
                                    "originalPrice",
                                    "requestedPrice");
                    case "contract" ->
                            List.of(
                                    "reason",
                                    "sourceId",
                                    "sourceType",
                                    "subscriptionId",
                                    "contractNo",
                                    "contractName",
                                    "memberName");
                    default -> List.of("reason");
                };
        if (raw != null)
            for (String key : fields) {
                Object value = raw.get(key);
                if (value instanceof String
                        || value instanceof Number
                        || value instanceof Boolean) {
                    if (Set.of("amount", "originalPrice", "requestedPrice").contains(key))
                        value =
                                new BigDecimal(value.toString())
                                        .setScale(2, RoundingMode.HALF_UP)
                                        .toPlainString();
                    else if (key.endsWith("Id")) value = value.toString();
                    result.put(key, value);
                }
            }
        return Collections.unmodifiableMap(result);
    }

    private String time(Instant at) {
        return at == null ? null : at.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }
}
