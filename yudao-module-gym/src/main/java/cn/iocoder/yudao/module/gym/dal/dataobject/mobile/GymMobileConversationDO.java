package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_conversation")
public class GymMobileConversationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long memberId;
    private Long creatorUserId;
    private java.time.Instant initiatedAt;
    private Long lastMessageId;
    private Long memberReadMessageId;
    private Integer memberUnreadCount;
}
