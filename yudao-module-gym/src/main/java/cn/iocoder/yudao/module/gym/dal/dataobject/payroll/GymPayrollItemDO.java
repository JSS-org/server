package cn.iocoder.yudao.module.gym.dal.dataobject.payroll;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_payroll_item")
public class GymPayrollItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long salaryAssignmentId;
    private Long payrollId;
    private Long employeeId;
    private String employeeName;
    private java.math.BigDecimal baseSalary;
    private java.math.BigDecimal cardCommission;
    private java.math.BigDecimal ptCommission;
    private java.math.BigDecimal lessonCommission;
    private java.math.BigDecimal allowances;
    private java.math.BigDecimal deductions;
    private java.math.BigDecimal grossAmount;
    private java.math.BigDecimal netAmount;
    private String calculationSnapshot;
    private String components;
}
