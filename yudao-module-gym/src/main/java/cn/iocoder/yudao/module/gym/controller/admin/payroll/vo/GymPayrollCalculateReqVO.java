package cn.iocoder.yudao.module.gym.controller.admin.payroll.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymPayrollCalculateReqVO {
    @NotNull @Positive private Long storeId;

    @NotNull
    @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])")
    private String month;
}
