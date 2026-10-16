package cn.iocoder.yudao.module.gym.controller.admin.marketing.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymFranchiseSubmitReqVO {
    @NotBlank
    @Size(max = 80)
    private String contactName;

    @NotBlank
    @Pattern(regexp = "1[3-9][0-9]{9}")
    private String phone;

    @NotBlank
    @Size(max = 100)
    private String preferredCity;

    @Size(max = 2000)
    private String message;
}
