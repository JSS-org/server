package cn.iocoder.yudao.module.gym.dal.dataobject.settlement;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_settlement_rule")
public class GymSettlementRuleDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Integer version;
    private String cycleUnit;
    private Integer cycleValue;
    private java.time.LocalDate effectiveFrom;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private java.time.LocalDate effectiveTo;

    private String status;
    private String items;
}
