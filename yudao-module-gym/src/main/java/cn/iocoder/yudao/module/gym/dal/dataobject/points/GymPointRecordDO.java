package cn.iocoder.yudao.module.gym.dal.dataobject.points;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_point_record")
public class GymPointRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long accountId;
    private Long memberId;
    private String businessKey;
    private String type;
    private Long delta;
    private Long balanceAfter;
    private String reason;
    private Long originalRecordId;
    private Instant postedAt;
}
