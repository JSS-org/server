package cn.iocoder.yudao.module.gym.dal.dataobject.sales;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_subscription")
public class GymSubscriptionDO extends TenantBaseDO {
 @TableId private Long id;
 private Long storeId;
 private Long memberId;
 private String memberName;
 private Long cardTemplateId;
 private String channel;
 private String status;
 private BigDecimal amount;
 private Integer billingDay;
 private String provider;
 private String providerAgreementId;
 private Instant requestedAt;
 private Instant signedAt;
 private Instant nextChargeAt;
 private Instant cancelledAt;
 private String failureReason;
}
