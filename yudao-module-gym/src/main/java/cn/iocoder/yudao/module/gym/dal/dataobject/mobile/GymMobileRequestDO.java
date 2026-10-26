package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_request")
public class GymMobileRequestDO extends TenantBaseDO {
    @TableId private Long id;
    private Integer userType;
    private Long userId;
    private String operation;
    private String requestKey;
    private String requestHash;
    private String responseJson;
}
