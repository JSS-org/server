package cn.iocoder.yudao.module.gym.controller.admin.member.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.time.LocalDate;

@Data
public class GymMemberSaveReqVO {
    @NotNull @Positive private Long storeId;

    @NotBlank
    @Pattern(regexp = "1[3-9][0-9]{9}")
    private String phone;

    @NotBlank
    @Size(max = 80)
    private String name;

    @Size(max = 80)
    private String nickname;

    @NotNull
    @Pattern(regexp = "male|female|unspecified")
    private String gender;

    @PastOrPresent private LocalDate birthday;

    @NotNull
    @Pattern(regexp = "active|inactive")
    private String status;
}
