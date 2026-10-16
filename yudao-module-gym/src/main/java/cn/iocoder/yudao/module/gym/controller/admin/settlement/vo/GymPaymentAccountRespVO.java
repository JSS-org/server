package cn.iocoder.yudao.module.gym.controller.admin.settlement.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class GymPaymentAccountRespVO {
    private String id;
    private String storeId;
    private String storeName;
    private String accountName;
    private String provider;
    private String merchantId;
    private String lastFour;
    private String accountMasked;
    private String credentialRef;
    private String bindingStatus;
    private Instant bindingAttemptedAt;
    private String bindingFailureReason;
}
