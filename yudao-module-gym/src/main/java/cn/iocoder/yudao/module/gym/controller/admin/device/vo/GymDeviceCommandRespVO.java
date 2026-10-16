package cn.iocoder.yudao.module.gym.controller.admin.device.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class GymDeviceCommandRespVO {
    private String id;
    private String deviceId;
    private String compartmentId;
    private String command;
    private String reason;
    private String requesterId;
    private String requesterName;
    private Instant requestedAt;
    private String status;
    private String failureReason;
    private Boolean simulated;
}
