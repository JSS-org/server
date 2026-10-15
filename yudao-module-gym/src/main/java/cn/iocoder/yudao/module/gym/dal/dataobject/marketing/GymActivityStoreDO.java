package cn.iocoder.yudao.module.gym.dal.dataobject.marketing;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_activity_store")
public class GymActivityStoreDO extends TenantBaseDO {
 @TableId private Long id;private Long activityId;private Long storeId;
}
