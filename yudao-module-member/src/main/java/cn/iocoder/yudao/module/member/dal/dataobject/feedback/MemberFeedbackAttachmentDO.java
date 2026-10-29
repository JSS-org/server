package cn.iocoder.yudao.module.member.dal.dataobject.feedback;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("member_feedback_attachment")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberFeedbackAttachmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private Long feedbackId;
    private Integer sort;
    private String contentType;
    private byte[] content;
}
