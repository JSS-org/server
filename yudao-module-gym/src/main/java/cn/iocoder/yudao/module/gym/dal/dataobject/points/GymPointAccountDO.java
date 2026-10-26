package cn.iocoder.yudao.module.gym.dal.dataobject.points;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_point_account")
public class GymPointAccountDO extends TenantBaseDO {
    @TableId private Long id;
    private Long memberId;
    private Long balance;
    private Long totalEarned;
    private Long totalSpent;
    private Long refundReserve;
}
