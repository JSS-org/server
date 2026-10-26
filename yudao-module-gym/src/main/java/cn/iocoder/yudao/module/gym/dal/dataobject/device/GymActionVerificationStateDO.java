package cn.iocoder.yudao.module.gym.dal.dataobject.device;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_action_verification_state")
public class GymActionVerificationStateDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private Integer failureCount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant lockedUntil;

    private Instant lastAttemptAt;
}
