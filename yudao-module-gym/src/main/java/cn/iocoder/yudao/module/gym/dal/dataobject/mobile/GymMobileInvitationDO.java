package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_invitation")
public class GymMobileInvitationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long inviterMemberId;
    private Long inviterUserId;
    private String inviteCode;
    private String sharePage;
    private Instant issuedAt;
    private Instant expiresAt;
}
