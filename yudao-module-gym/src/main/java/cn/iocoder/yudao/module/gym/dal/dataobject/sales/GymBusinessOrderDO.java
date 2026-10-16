package cn.iocoder.yudao.module.gym.dal.dataobject.sales;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_business_order")
public class GymBusinessOrderDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long memberId;
    private Long employeeId;
    private String orderNo;
    private String memberName;
    private String businessType;
    private String channel;
    private String status;
    private BigDecimal payableAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private Instant orderedAt;
    private String requestKey;
}
