package cn.iocoder.yudao.module.gym.dal.dataobject.device;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_action_confirmation")
public class GymActionConfirmationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private Long storeId;
    private Long deviceId;
    private Long compartmentId;
    private String action;
    @ToString.Exclude private String tokenHash;
    @ToString.Exclude private String credentialFingerprint;
    private Long verificationCounter;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant consumedAt;
    private Long consumedCommandId;
}
