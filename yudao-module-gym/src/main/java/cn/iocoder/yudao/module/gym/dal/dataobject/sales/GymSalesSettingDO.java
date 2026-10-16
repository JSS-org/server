package cn.iocoder.yudao.module.gym.dal.dataobject.sales;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_sales_setting")
public class GymSalesSettingDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private String kind;
    private String content;
    private Long revision;
}
