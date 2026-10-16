package cn.iocoder.yudao.module.gym.controller.admin.device.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymTutorialStatusReqVO {
    @NotNull
    @Pattern(regexp = "active|inactive")
    private String status;
}
