package cn.iocoder.yudao.module.gym.controller.admin.marketing.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class GymActivityRegistrationRespVO {
    private String id;
    private String activityId;
    private String memberId;
    private String memberName;
    private String storeId;
    private String storeName;
    private String status;
    private Instant registeredAt;
    private Instant cancelledAt;
}
