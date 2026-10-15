package cn.iocoder.yudao.module.gym.dal.dataobject.sales;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.Instant;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_order_refund_review")
public class GymOrderRefundReviewDO extends TenantBaseDO {
 @TableId private Long id;
 private Long orderId; private Long refundId; private String status; private String decision;
 private String memberCardIds; private String note; private Long reviewerId;
 private Instant reviewedAt; private Instant createdAt;
}
