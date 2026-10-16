package cn.iocoder.yudao.module.gym.dal.dataobject.payroll;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_payroll")
public class GymPayrollDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private String storeName;
    private String payrollMonth;
    private Integer employeeCount;
    private java.math.BigDecimal netAmount;
    private String status;
    private java.time.Instant calculatedAt;
    private java.time.Instant confirmedAt;
    private Long confirmedBy;
}
