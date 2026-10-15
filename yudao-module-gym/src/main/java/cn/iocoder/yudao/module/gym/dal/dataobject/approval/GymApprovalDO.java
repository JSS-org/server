package cn.iocoder.yudao.module.gym.dal.dataobject.approval;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.Instant;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_approval")
public class GymApprovalDO extends TenantBaseDO {
 @TableId private Long id;
 private Long storeId;
 private Long templateId;
 private String templateSnapshot;
 private String businessType;
 private Long sourceCoachId;
 private String title;
 private Long applicantUserId;
 private String applicantName;
 private String requestKey;
 private String requestHash;
 private String requestSnapshot;
 private String attachmentIds;
 private String status;
 private Integer currentStep;
 private Instant submittedAt;
 private Instant completedAt;
 private String businessResult;
}
