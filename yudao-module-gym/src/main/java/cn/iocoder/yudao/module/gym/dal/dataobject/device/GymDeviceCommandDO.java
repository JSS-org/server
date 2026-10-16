package cn.iocoder.yudao.module.gym.dal.dataobject.device;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_device_command")
public class GymDeviceCommandDO extends TenantBaseDO {
    @TableId private Long id;
    private Long deviceId;
    private Long compartmentId;
    private String command;
    private String reason;
    private Long requesterId;
    private String requesterName;
    private java.time.Instant requestedAt;
    private String status;
    private String failureReason;
    private Boolean simulated;
    private String requestKey;
    private String requestHash;
}
