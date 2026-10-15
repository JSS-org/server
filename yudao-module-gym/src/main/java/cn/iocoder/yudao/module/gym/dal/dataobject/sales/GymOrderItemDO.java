package cn.iocoder.yudao.module.gym.dal.dataobject.sales;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_order_item")
public class GymOrderItemDO extends TenantBaseDO {
 @TableId private Long id;
 private Long orderId;
 private String productType;
 private Long productId;
 private String productNameSnapshot;
 private Integer productVersion;
 private Integer quantity;
 private BigDecimal unitPrice;
 private BigDecimal payableAmount;
 private String entitlementIds;
}
