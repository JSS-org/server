package cn.iocoder.yudao.module.gym.dal.dataobject.points;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_point_redemption")
public class GymPointRedemptionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private String requestKey;
    private Long memberId;
    private Long storeId;
    private Long productId;
    private Long productRevision;
    private Long requiredPoints;
    private Boolean stockReserved;
    private String templateSnapshot;
    private String status;
    private Long debitRecordId;
    private Long refundRecordId;
    private Long memberCardId;
    private Instant requestedAt;
    private Instant completedAt;
    private Integer failureCode;
}
