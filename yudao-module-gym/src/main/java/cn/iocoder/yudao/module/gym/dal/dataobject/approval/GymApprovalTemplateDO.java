package cn.iocoder.yudao.module.gym.dal.dataobject.approval;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper=true)
@TableName("gym_approval_template")
public class GymApprovalTemplateDO extends TenantBaseDO {
    @TableId private Long id;
    private Long familyId;
    private Integer version;
    private String name;
    private String businessType;
    private String steps;
    private String status;
}
