package cn.iocoder.yudao.module.gym.dal.dataobject.device;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_device")
public class GymDeviceDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private String name;
    private String type;
    private String provider;
    private String deviceUid;
    private String location;
    private String status;
    private Boolean online;
    private java.time.Instant lastSeenAt;
}
