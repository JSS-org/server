package cn.iocoder.yudao.module.gym.dal.dataobject.marketing;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_franchise_inquiry")
public class GymFranchiseInquiryDO extends TenantBaseDO {
    @TableId private Long id;
    private String contactName;
    private String phone;
    private String preferredCity;
    private String message;
    private String status;
    private java.time.Instant receivedAt;
    private java.time.Instant contactedAt;
    private java.time.Instant closedAt;
    private Long handledBy;
    private String requestKey;
    private String requestHash;
    private String channel;
}
