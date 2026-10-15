package cn.iocoder.yudao.module.gym.dal.dataobject.member;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_training_plan")
public class GymTrainingPlanDO extends TenantBaseDO {
 @TableId private Long id;
 private Long memberId;
 private Long storeId;
 private String title;
 private String content;
 private Long fileId;
 private String status;
}
