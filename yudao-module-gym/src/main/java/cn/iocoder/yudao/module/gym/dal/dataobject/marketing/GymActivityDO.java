package cn.iocoder.yudao.module.gym.dal.dataobject.marketing;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_activity")
public class GymActivityDO extends TenantBaseDO {
 @TableId private Long id;
 private String name;private String description;private java.time.Instant registrationStartsAt;private java.time.Instant registrationEndsAt;
 private java.time.Instant startsAt;private java.time.Instant endsAt;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer capacity;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long coverFileId;
 private String status;
}
