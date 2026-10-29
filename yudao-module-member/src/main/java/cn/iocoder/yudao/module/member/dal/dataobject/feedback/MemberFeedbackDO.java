package cn.iocoder.yudao.module.member.dal.dataobject.feedback;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("member_feedback")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberFeedbackDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private String content;
}
