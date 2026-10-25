package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_message")
public class GymMobileMessageDO extends TenantBaseDO {
    @TableId private Long id;
    private Long conversationId;
    private Integer senderType;
    private Long senderUserId;
    private String content;
    private java.time.Instant sentAt;
}
