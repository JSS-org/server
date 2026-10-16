package cn.iocoder.yudao.module.gym.dal.dataobject.contract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_contract_reminder")
public class GymContractReminderDO extends TenantBaseDO {
    @TableId private Long id;
    private Long contractId;
    private String requestKey;
    private String channel;
    private String status;
    private String failureReason;
    private Instant scheduledAt;
    private Instant completedAt;
    private Long requestedBy;
}
