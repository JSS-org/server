package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_identity")
public class GymMobileIdentityDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private Long memberId;
    private Long defaultStoreId;
    private String source;
    private String inviteCode;
    private Long avatarFileId;
    private String faceStatus;
}
