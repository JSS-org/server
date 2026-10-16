package cn.iocoder.yudao.module.gym.controller.admin.content.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class GymQrCodeRespVO {
    private String id;
    private String storeId;
    private String storeName;
    private String platform;
    private String sceneType;
    private String sceneId;
    private String status;
    private String failureReason;
    private String providerCodeFileId;
    private Instant createdAt;
}
