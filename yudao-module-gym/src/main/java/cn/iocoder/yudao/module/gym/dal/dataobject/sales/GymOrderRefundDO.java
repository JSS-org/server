package cn.iocoder.yudao.module.gym.dal.dataobject.sales;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_order_refund")
public class GymOrderRefundDO extends TenantBaseDO {
 @TableId private Long id;
 private Long orderId;
 private Long paymentId;
 private String provider;
 private String providerRefundId;
 private BigDecimal amount;
 private String status;
 private Instant refundedAt;
 private String failureReason;
}
