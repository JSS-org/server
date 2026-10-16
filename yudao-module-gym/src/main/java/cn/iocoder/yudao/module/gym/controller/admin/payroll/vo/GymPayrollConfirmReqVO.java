package cn.iocoder.yudao.module.gym.controller.admin.payroll.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymPayrollConfirmReqVO {
    @NotNull
    @Pattern(regexp = "confirmed")
    private String status;
}
