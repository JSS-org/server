package cn.iocoder.yudao.module.gym.dal.dataobject.member;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.Instant;
import java.math.BigDecimal;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_measurement")
public class GymMeasurementDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long memberId;
    private Instant measuredAt;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bodyFatPercent;
    private BigDecimal muscleMassKg;
    private String note;
    private Long reportFileId;
}
