package cn.iocoder.yudao.module.gym.dal.dataobject.finance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_financial_event")
public class GymFinancialEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long employeeId;
    private Long businessOrderId;
    private String eventType;
    private String productType;
    private java.math.BigDecimal amount;
    private String sourceType;
    private String sourceId;
    private java.time.Instant occurredAt;
}
