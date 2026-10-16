package cn.iocoder.yudao.module.gym.dal.dataobject.contract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_contract_template")
public class GymContractTemplateDO extends TenantBaseDO {
    @TableId private Long id;
    private Long familyId;
    private Integer version;
    private String name;
    private String applicableType;
    private Integer reminderDays;
    private Long fileId;
    private String status;
}
