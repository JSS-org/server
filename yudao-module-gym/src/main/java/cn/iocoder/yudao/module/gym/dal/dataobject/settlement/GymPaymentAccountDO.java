package cn.iocoder.yudao.module.gym.dal.dataobject.settlement;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_payment_account")
public class GymPaymentAccountDO extends TenantBaseDO {
 @TableId private Long id;
 private Long storeId; private Long scopeKey; private String accountName; private String provider; private String merchantId; private String lastFour;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String credentialRef;
 private String bindingStatus;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private java.time.Instant bindingAttemptedAt;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String bindingFailureReason;
}
