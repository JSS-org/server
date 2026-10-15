package cn.iocoder.yudao.module.gym.dal.dataobject.salary;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_salary_template")
public class GymSalaryTemplateDO extends TenantBaseDO {
    @TableId private Long id;
    private Long familyId;
    private String name;
    private Integer version;
    private java.math.BigDecimal baseSalary;
    private java.time.LocalDate effectiveFrom;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private java.time.LocalDate effectiveTo;
    private String status;
    private String rules;
}
