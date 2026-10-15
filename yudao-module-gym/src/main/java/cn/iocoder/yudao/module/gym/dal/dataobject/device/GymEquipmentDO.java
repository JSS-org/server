package cn.iocoder.yudao.module.gym.dal.dataobject.device;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_equipment")
public class GymEquipmentDO extends TenantBaseDO {
 @TableId private Long id;
 private Long storeId;private String code;private String name;private String model;private String status;
}
