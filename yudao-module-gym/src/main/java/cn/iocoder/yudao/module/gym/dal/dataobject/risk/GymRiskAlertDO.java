package cn.iocoder.yudao.module.gym.dal.dataobject.risk;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_risk_alert")
public class GymRiskAlertDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long deviceId;
    private Long memberId;
    private String type;
    private String sourceEventKey;
    private String sourceSnapshot;
    private Instant occurredAt;
    private String evidenceFileIds;
    private String status;
    private Integer version;
}
