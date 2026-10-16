package cn.iocoder.yudao.module.gym.controller.admin.device.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymEquipmentSaveReqVO {
    @NotNull @Positive private Long storeId;

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 100)
    private String model;

    @Pattern(regexp = "active|inactive")
    private String status;
}
