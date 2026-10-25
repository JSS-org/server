package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_notification")
public class GymMobileNotificationDO extends TenantBaseDO {
    @TableId private Long id;
    private Integer recipientType;
    private Long recipientUserId;
    private Long memberId;
    private Long storeId;
    private String type;
    private String sourceType;
    private Long businessId;
    private Long targetId;
    private String eventKind;
    private String eventKey;
    private java.time.Instant occurredAt;
    private String title;
    private String content;
    private java.time.Instant publishedAt;
    private java.time.Instant readAt;
}
