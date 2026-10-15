package cn.iocoder.yudao.module.gym.dal.dataobject.salary;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_salary_assignment")
public class GymSalaryAssignmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long employeeId;
    private Long storeId;
    private Long templateId;
    private java.time.LocalDate effectiveFrom;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private java.time.LocalDate effectiveTo;
}
