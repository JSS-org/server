package cn.iocoder.yudao.module.gym.controller.admin.marketing.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymFranchisePatchReqVO {
    @NotNull
    @Pattern(regexp = "new|contacted|closed")
    private String status;
}
