package cn.iocoder.yudao.module.gym.dal.dataobject.device;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_equipment_tutorial")
public class GymEquipmentTutorialDO extends TenantBaseDO {
 @TableId private Long id;
 private Long equipmentId;private String title;private Long fileId;private String instructions;private Integer sortOrder;private String status;
}
