package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import org.springframework.web.multipart.MultipartFile;

@Data
@io.swagger.v3.oas.annotations.media.Schema(description = "工作台业务文件上传；目标归属保存后不可变更")
public class GymMobileWorkFileUploadReqVO {
    @NotNull private MultipartFile file;

    @NotBlank
    @Pattern(regexp = "coach_profile|body_report|training_plan|contract")
    private String purpose;

    @NotNull @Positive private Long storeId;
    @Positive private Long memberId;
}
