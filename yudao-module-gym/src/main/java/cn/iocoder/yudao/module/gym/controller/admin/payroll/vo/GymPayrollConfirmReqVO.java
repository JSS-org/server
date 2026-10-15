package cn.iocoder.yudao.module.gym.controller.admin.payroll.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data public class GymPayrollConfirmReqVO { @NotNull @Pattern(regexp="confirmed") private String status; }
