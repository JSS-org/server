package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobilePayrollVO.*;
import cn.iocoder.yudao.module.gym.controller.admin.payroll.vo.GymPayrollRespVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.payroll.*;
import cn.iocoder.yudao.module.gym.dal.mysql.payroll.*;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMobilePayrollService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private GymPayrollMapper payrolls;
    @Resource private GymPayrollItemMapper items;
    @Resource private Validator validator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Payroll> mine(OwnPage req) {
        Long employee = access.employee();
        validate(req, req.getMonth());
        var q = filter(null, req.getMonth(), true).eq(GymPayrollItemDO::getEmployeeId, employee);
        var page = items.selectPage(req, q);
        return project(page);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Payroll mine(Long id) {
        Long employee = access.employee();
        positive(id);
        var item =
                items.selectOne(
                        filter(null, null, true)
                                .eq(GymPayrollItemDO::getEmployeeId, employee)
                                .eq(GymPayrollItemDO::getId, id));
        if (item == null) throw exception(PAYROLL_NOT_EXISTS);
        return project(List.of(item)).getFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Payroll> store(StorePage req) {
        access.requireAdmin();
        validate(req, req.getMonth());
        scope.check(req.getStoreId());
        var q =
                filter(req.getStoreId(), req.getMonth(), false)
                        .eq(
                                req.getEmployeeId() != null,
                                GymPayrollItemDO::getEmployeeId,
                                req.getEmployeeId());
        return project(items.selectPage(req, q));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Payroll store(Long id) {
        access.requireAdmin();
        positive(id);
        var item = items.selectById(id);
        if (item == null) throw exception(PAYROLL_NOT_EXISTS);
        var parent = payrolls.selectById(item.getPayrollId());
        if (parent == null) throw exception(PAYROLL_NOT_EXISTS);
        scope.check(parent.getStoreId());
        return project(item, parent);
    }

    private void positive(Long id) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
    }

    private void validate(Object req, String month) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        if (month != null) {
            try {
                if (YearMonth.parse(month).getYear() < 1000) throw new DateTimeException("year");
            } catch (DateTimeException e) {
                throw exception(PAYROLL_MONTH_INVALID);
            }
        }
    }

    private LambdaQueryWrapper<GymPayrollItemDO> filter(
            Long store, String month, boolean published) {
        String sql =
                "SELECT 1 FROM gym_payroll p WHERE p.id=gym_payroll_item.payroll_id AND p.tenant_id={0} AND p.deleted=0";
        var args = new ArrayList<Object>();
        args.add(TenantContextHolder.getRequiredTenantId());
        if (store != null) {
            sql += " AND p.store_id={" + args.size() + "}";
            args.add(store);
        }
        if (month != null) {
            sql += " AND p.payroll_month={" + args.size() + "}";
            args.add(month);
        }
        if (published) sql += " AND p.status='confirmed' AND p.published_at IS NOT NULL";
        return new LambdaQueryWrapper<GymPayrollItemDO>()
                .exists(sql, args.toArray())
                .orderByDesc(GymPayrollItemDO::getPayrollId)
                .orderByAsc(GymPayrollItemDO::getEmployeeId)
                .orderByAsc(GymPayrollItemDO::getId);
    }

    private PageResult<Payroll> project(PageResult<GymPayrollItemDO> page) {
        return new PageResult<>(project(page.getList()), page.getTotal());
    }

    private List<Payroll> project(List<GymPayrollItemDO> rows) {
        if (rows.isEmpty()) return List.of();
        var parents =
                payrolls
                        .selectBatchIds(
                                rows.stream()
                                        .map(GymPayrollItemDO::getPayrollId)
                                        .distinct()
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(GymPayrollDO::getId, x -> x));
        return rows.stream().map(x -> project(x, parents.get(x.getPayrollId()))).toList();
    }

    private Payroll project(GymPayrollItemDO row, GymPayrollDO parent) {
        if (parent == null) throw exception(PAYROLL_NOT_EXISTS);
        var components =
                JsonUtils.parseArray(row.getComponents(), GymPayrollRespVO.Component.class);
        var detail =
                components.stream()
                        .map(c -> new Item(c.getLabel(), money(c.getAmount()), description(c)))
                        .toList();
        return new Payroll(
                row.getId().toString(),
                parent.getStoreId().toString(),
                row.getEmployeeId().toString(),
                parent.getPayrollMonth(),
                parent.getPublishedAt() == null ? parent.getStatus() : "published",
                money(row.getBaseSalary()),
                money(
                        row.getCardCommission()
                                .add(row.getPtCommission())
                                .add(row.getLessonCommission())),
                money(row.getAllowances()),
                money(row.getDeductions()),
                money(row.getNetAmount()),
                detail);
    }

    private String description(GymPayrollRespVO.Component c) {
        return switch (c.getComponent()) {
            case "base" -> "整月底薪快照";
            case "card_sales", "pt_sales" -> "销售提成；计薪基数=" + money(c.getBasisAmount());
            case "lessons" -> "已确认课消；计薪课时=" + c.getBasisQuantity();
            case "allowance" -> "固定加项";
            case "deduction" -> "固定扣项（明细为负值）";
            default -> "已保存的计算项目";
        };
    }

    private String money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }
}
