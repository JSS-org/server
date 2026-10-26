package cn.iocoder.yudao.module.gym.dal.dataobject.groupbuy;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@TableName("gym_coupon_verification")
public class GymCouponVerificationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private Long memberId;
    private Long storeId;
    private Long previewId;
    private String requestKey;
    private String providerRequestId;
    private String platform;
    private String couponHash;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String claimedCouponHash;

    private String scopeSnapshot;
    private String templateSnapshot;
    private Long cardTemplateId;
    private Long dealId;
    private Long dealGroupId;
    private String status;
    private String stage;
    private String confirmationSnapshot;
    private String providerFlowId;
    private Long memberCardId;
    private Long verificationId;
    private Instant requestedAt;
    private Instant consumeStartedAt;
    private Instant confirmedAt;
    private Instant completedAt;
    private Instant nextCheckAt;
    private Integer queryCount;
    private String observationSnapshot;
    private Integer lastErrorCode;
}
