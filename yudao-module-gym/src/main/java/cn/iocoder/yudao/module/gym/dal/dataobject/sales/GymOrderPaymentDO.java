package cn.iocoder.yudao.module.gym.dal.dataobject.sales;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_order_payment")
public class GymOrderPaymentDO extends TenantBaseDO {
 @TableId private Long id;
 private Long orderId;
 private String provider;
 private String providerPaymentId;
 private BigDecimal amount;
 private String status;
 private Instant paidAt;
 private String failureReason;
}
