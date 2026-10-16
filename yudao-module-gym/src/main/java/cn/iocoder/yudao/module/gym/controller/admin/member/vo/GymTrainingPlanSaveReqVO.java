package cn.iocoder.yudao.module.gym.controller.admin.member.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymTrainingPlanSaveReqVO {
    @NotNull @Positive private Long memberId;
    @NotNull @Positive private Long storeId;

    @NotBlank
    @Size(max = 120)
    private String title;

    @Size(max = 20000)
    private String content;

    @Positive private Long fileId;

    @NotNull
    @Pattern(regexp = "active|archived")
    private String status;

    @JsonIgnore private boolean fileIdPresent;

    public void setFileId(Long value) {
        fileId = value;
        fileIdPresent = true;
    }
}
