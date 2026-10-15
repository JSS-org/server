package cn.iocoder.yudao.module.gym.dal.dataobject.card;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_card_template")
public class GymCardTemplateDO extends TenantBaseDO {
    @TableId private Long id;
    private Long familyId;
    private Integer version;
    private String name;
    private String kind;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Integer durationDays;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Integer includedUses;
    private java.math.BigDecimal price;
    private String memberPrefix;
    private String accessScope;
    private String storeIds;
    private String status;
}
