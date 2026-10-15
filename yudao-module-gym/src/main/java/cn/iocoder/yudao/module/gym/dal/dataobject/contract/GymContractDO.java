package cn.iocoder.yudao.module.gym.dal.dataobject.contract;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.Instant;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_contract")
public class GymContractDO extends TenantBaseDO {
 @TableId private Long id;
 private String contractNo;
 private String name;
 private Long storeId;
 private Long memberId;
 private Long templateId;
 private String templateSnapshot;
 private String businessSnapshot;
 private String sourceType;
 private Long sourceId;
 private Long unsignedFileId;
 private Long signedFileId;
 private String status;
 private String approvalStatus;
 private Long approvedApprovalId;
 private Instant approvedAt;
 private String signingStatus;
 private Instant dueAt;
 private Instant signedAt;
 private String signIp;
 private String signDeviceUid;
 private String signUserAgent;
 private Instant issuedAt;
 private Instant cancelledAt;
}
