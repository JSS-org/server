package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_feedback_attachment")
public class GymFeedbackAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long feedbackId;
    private Long fileId;
}
