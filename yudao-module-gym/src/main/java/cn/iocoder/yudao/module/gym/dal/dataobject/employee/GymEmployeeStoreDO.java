package cn.iocoder.yudao.module.gym.dal.dataobject.employee;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_employee_store")
public class GymEmployeeStoreDO extends TenantBaseDO {
    @TableId private Long id;
    private Long employeeId;
    private Long storeId;
    private Boolean isPrimary;
    private Boolean isManager;
    private LocalDate joinedOn;
}
