package cn.iocoder.yudao.module.gym.controller.admin.contract.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymContractReminderReqVO {
    @NotNull
    @Pattern(regexp = "sms|miniapp|app")
    private String channel;
}
