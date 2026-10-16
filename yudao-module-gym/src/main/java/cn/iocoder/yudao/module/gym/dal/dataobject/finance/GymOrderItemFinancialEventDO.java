package cn.iocoder.yudao.module.gym.dal.dataobject.finance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_order_item_financial_event")
public class GymOrderItemFinancialEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long financialEventId;
    private Long orderItemId;
    private BigDecimal amount;
}
