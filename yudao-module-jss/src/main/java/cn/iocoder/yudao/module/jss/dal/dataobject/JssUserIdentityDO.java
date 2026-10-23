package cn.iocoder.yudao.module.jss.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("jss_user_identity")
@Data
@EqualsAndHashCode(callSuper = true)
public class JssUserIdentityDO extends TenantBaseDO {
    @TableId private Long id;
    private Long memberUserId;
    private Long adminUserId;
    private Integer status;
    private Long bindingVersion;
    private Long boundBy;
    private LocalDateTime boundAt;
}
