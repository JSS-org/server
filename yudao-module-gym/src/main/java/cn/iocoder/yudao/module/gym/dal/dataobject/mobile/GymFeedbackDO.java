package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_feedback")
public class GymFeedbackDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long memberId;
    private String type;
    private String content;
    private Integer rating;
    private Boolean allowCardContext;
    private String cardContext;
    private String status;
    private java.time.Instant submittedAt;
    private String reply;
    private Long repliedBy;
    private java.time.Instant repliedAt;
}
