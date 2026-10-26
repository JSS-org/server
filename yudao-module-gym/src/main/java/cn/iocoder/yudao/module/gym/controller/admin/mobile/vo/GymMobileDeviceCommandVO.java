package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

public record GymMobileDeviceCommandVO(
        String id,
        String deviceId,
        String command,
        String status,
        String requestedAt,
        String completedAt,
        String failureReason) {}
