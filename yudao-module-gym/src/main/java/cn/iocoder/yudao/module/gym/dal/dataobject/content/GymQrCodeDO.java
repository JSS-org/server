package cn.iocoder.yudao.module.gym.dal.dataobject.content;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("gym_qr_code")
public class GymQrCodeDO extends TenantBaseDO {
 @TableId private Long id;private Long storeId;private String platform;private String sceneType;private Long sceneId;private Long sceneKey;
 private String status;private String failureReason;private Long providerCodeFileId;private java.time.Instant createdAt;
}
