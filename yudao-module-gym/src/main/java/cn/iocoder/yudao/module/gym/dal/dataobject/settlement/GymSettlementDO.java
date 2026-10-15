package cn.iocoder.yudao.module.gym.dal.dataobject.settlement;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** 真实支付/分账链路的结果快照；没有公开创建到账记录的接口。 */
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_settlement")
public class GymSettlementDO extends TenantBaseDO {
 @TableId private Long id;
 private Long storeId; private String storeName; private Long financialEventId; private Long ruleId;
 private String ruleSnapshot; private String orderNo; private java.math.BigDecimal baseAmount;
 private java.time.Instant dueAt; private String status; private String items;
}
