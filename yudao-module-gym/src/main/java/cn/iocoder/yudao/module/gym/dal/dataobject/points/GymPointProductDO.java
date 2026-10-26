package cn.iocoder.yudao.module.gym.dal.dataobject.points;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_point_product")
public class GymPointProductDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long cardProductId;
    private String name;
    private Long requiredPoints;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer stock;

    private Boolean enabled;
    private Long revision;
}
