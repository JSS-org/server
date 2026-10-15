package cn.iocoder.yudao.module.gym.dal.dataobject.sales;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_verification")
public class GymVerificationDO extends TenantBaseDO {
 @TableId private Long id;
 private Long storeId;
 private Long memberId;
 private String memberName;
 private String type;
 private String channel;
 private String status;
 private String providerReference;
 private String requestKey;
 private Long memberCardId;
 private Long checkinId;
 private Instant verifiedAt;
 private String failureReason;
}
