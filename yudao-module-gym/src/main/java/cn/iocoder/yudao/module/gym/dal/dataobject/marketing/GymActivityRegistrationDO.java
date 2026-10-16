package cn.iocoder.yudao.module.gym.dal.dataobject.marketing;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_activity_registration")
public class GymActivityRegistrationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long activityId;
    private Long storeId;
    private String storeName;
    private Long memberId;
    private String memberName;
    private String status;
    private java.time.Instant registeredAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private java.time.Instant cancelledAt;
}
