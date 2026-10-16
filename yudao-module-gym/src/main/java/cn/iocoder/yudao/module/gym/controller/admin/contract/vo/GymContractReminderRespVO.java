package cn.iocoder.yudao.module.gym.controller.admin.contract.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class GymContractReminderRespVO {
    private String id;
    private String contractId;
    private String channel;
    private String status;
    private String failureReason;
    private Instant scheduledAt;
    private Instant completedAt;
}
