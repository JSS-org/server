package cn.iocoder.yudao.module.gym.dal.dataobject.employee;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_employee")
public class GymEmployeeDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private String employeeNo;
    private String name;
    private String phone;
    private String jobTitle;
    private LocalDate hiredOn;
    private String status;
}
