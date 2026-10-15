package cn.iocoder.yudao.module.gym.dal.dataobject.member;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.Instant;
import java.math.BigDecimal;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_blacklist")
public class GymBlacklistDO extends TenantBaseDO {
    @TableId private Long id;
    private Long memberId;
    private Boolean blockEntry;
    private Boolean blockPurchase;
    private String reason;
    private Instant startsAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Instant endsAt;
    private Instant revokedAt;
}
