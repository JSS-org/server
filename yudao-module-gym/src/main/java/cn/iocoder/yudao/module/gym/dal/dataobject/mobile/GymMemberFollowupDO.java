package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_member_followup")
public class GymMemberFollowupDO extends TenantBaseDO {
    @TableId private Long id;
    private Long memberId;
    private Long storeId;
    private Long employeeId;
    private String content;
    private Instant nextContactAt;
}
