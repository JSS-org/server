package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_file_owner")
public class GymMobileFileOwnerDO extends TenantBaseDO {
    @TableId private Long id;
    private Long fileId;
    private Long memberId;
    private String purpose;
}
