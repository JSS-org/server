package cn.iocoder.yudao.module.gym.dal.dataobject.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_approval_task")
public class GymApprovalTaskDO extends TenantBaseDO {
    @TableId private Long id;
    private Long approvalId;
    private Integer stepNo;
    private Long approverEmployeeId;
    private Long approverUserId;
    private String approverName;
    private String status;
    private String comment;
    private Instant decidedAt;
}
