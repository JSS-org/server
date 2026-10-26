package cn.iocoder.yudao.module.gym.dal.dataobject.groupbuy;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_coupon_preview")
public class GymCouponPreviewDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private Long memberId;
    private Long storeId;
    private String platform;
    private String couponHash;
    private String couponCiphertext;
    private Long dealId;
    private Long dealGroupId;
    private Long cardTemplateId;
    private String mappingFingerprint;
    private Long settingsRevision;
    private String templateSnapshot;
    private String productName;
    private Boolean redeemable;
    private String reason;
    private Instant issuedAt;
    private Instant expiresAt;
}
