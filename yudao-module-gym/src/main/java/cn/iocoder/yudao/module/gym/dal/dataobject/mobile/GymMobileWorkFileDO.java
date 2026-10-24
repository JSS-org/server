package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_work_file")
public class GymMobileWorkFileDO extends TenantBaseDO {
    @TableId private Long id;
    private Long fileId;
    private Long storeId;
    private String purpose;
    private Long memberId;
    private Long employeeId;
}
