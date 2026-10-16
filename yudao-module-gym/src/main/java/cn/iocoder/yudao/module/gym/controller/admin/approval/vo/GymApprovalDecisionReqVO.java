package cn.iocoder.yudao.module.gym.controller.admin.approval.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymApprovalDecisionReqVO {
    @NotNull
    @Pattern(regexp = "approve|reject")
    private String decision;

    @Size(max = 2000)
    private String comment;
}
