package cn.iocoder.yudao.module.gym.dal.dataobject.sales;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_refund_application")
public class GymRefundApplicationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long orderId;
    private Long storeId;
    private Long memberId;

    /** Always a member_user ID, never a system_users ID. */
    private Long requesterUserId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long activeOrderId;

    private String status;
    private BigDecimal requestedAmount;
    private BigDecimal refundedAmount;
    private String reason;
    private Integer version;
    private Instant submittedAt;
    private Long reviewerUserId;
    private Instant reviewedAt;
    private String reviewNote;
    private String failureReason;
}
