package cn.iocoder.yudao.module.gym.dal.dataobject.content;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_client_setting")
public class GymClientSettingDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long scopeKey;
    private String platform;
    private String content;
    private Long revision;
}
