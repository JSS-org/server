package cn.iocoder.yudao.module.gym.controller.admin.contract.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.time.Instant;

@Data
public class GymContractCreateReqVO {
    @NotNull
    @Pattern(regexp = "member-cards|course-packs")
    private String sourceType;

    @NotNull @Positive private Long sourceId;
    @Positive private Long templateId;
    private Instant dueAt;
}
