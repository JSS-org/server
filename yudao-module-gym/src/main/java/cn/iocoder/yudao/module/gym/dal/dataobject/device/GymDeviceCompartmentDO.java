package cn.iocoder.yudao.module.gym.dal.dataobject.device;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_device_compartment")
public class GymDeviceCompartmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long deviceId;
    private String number;
    private String status;
    private Boolean configured;
    private String occupancyState;
}
