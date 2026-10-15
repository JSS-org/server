package cn.iocoder.yudao.module.gym.dal.dataobject.card;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_member_store")
public class GymMemberStoreDO extends TenantBaseDO {
    @TableId private Long id;
    private Long memberId;
    private Long storeId;
    private java.time.Instant joinedAt;
}
