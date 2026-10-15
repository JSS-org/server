package cn.iocoder.yudao.module.gym.dal.dataobject.sales;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_subscription_cycle")
public class GymSubscriptionCycleDO extends TenantBaseDO {
 @TableId private Long id;
 private Long subscriptionId;
 private Long orderId;
 private LocalDate periodStart;
 private LocalDate periodEnd;
 private BigDecimal amount;
 private BigDecimal paidAmount;
 private String status;
 private Instant chargedAt;
 private String failureReason;
}
