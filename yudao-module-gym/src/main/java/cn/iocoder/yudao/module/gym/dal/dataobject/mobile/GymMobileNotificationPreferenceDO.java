package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_notification_preference")
public class GymMobileNotificationPreferenceDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private Long memberId;
    private String templateKey;
    private Boolean enabled;
    private java.time.Instant savedAt;
}
