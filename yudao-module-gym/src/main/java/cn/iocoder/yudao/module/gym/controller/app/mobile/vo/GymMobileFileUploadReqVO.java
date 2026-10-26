package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import org.springframework.web.multipart.MultipartFile;

@Data
public class GymMobileFileUploadReqVO {
    @NotNull private MultipartFile file;

    @NotNull
    @Pattern(regexp = "avatar|face|feedback")
    private String purpose;

    @Positive private Long storeId;
}
