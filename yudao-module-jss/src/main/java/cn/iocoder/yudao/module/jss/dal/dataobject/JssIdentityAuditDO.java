package cn.iocoder.yudao.module.jss.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("jss_identity_audit")
@Data
@EqualsAndHashCode(callSuper = true)
public class JssIdentityAuditDO extends TenantBaseDO {
    @TableId private Long id;
    private Long identityId;
    private Long memberUserId;
    private Long adminUserId;
    private Long bindingVersion;
    private String action;
    private Long operatorId;
    private String reason;
}
