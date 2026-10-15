package cn.iocoder.yudao.module.gym.dal.dataobject.sales;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.Instant;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_fulfillment_task")
public class GymFulfillmentTaskDO extends TenantBaseDO {
 @TableId private Long id;
 private Long orderId; private Long principalUserId; private String status;
 private Integer attemptCount; private Integer attemptLimit;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private Instant nextAttemptAt;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private Instant lastAttemptAt;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private Instant leaseUntil;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String claimToken;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String failureReason;
}
