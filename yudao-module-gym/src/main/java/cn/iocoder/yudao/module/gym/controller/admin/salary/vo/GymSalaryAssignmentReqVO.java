package cn.iocoder.yudao.module.gym.controller.admin.salary.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.time.LocalDate;

@Data
public class GymSalaryAssignmentReqVO {
    @Positive private Long employeeId;
    @NotNull @Positive private Long storeId;
    @NotNull @Positive private Long templateId;
    @NotNull private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean effectiveToPresent;

    public void setEffectiveTo(LocalDate value) {
        effectiveTo = value;
        effectiveToPresent = true;
    }
}
