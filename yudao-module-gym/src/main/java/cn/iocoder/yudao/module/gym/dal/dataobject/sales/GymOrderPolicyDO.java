package cn.iocoder.yudao.module.gym.dal.dataobject.sales;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_order_policy")
public class GymOrderPolicyDO extends TenantBaseDO {
    @TableId private Long id;
    private Long orderId;
    private String requestHash;
    private String termsSnapshot;
    private Boolean approvalRequired;
    private Boolean contractRequired;
    private Long approvedApprovalId;
    private java.time.Instant fulfilledAt;
}
