package cn.iocoder.yudao.module.gym.dal.dataobject.contract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_contract_event")
public class GymContractEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long contractId;
    private String type;
    private Instant occurredAt;
    private Long actorUserId;
    private String ip;
    private String detail;
}
