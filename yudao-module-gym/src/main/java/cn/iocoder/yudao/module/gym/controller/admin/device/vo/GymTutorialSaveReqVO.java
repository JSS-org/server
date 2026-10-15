package cn.iocoder.yudao.module.gym.controller.admin.device.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data public class GymTutorialSaveReqVO {
 @NotBlank @Size(max=100) private String title;
 @NotNull @Positive private Long fileId;
 @Size(max=4000) private String instructions;
 @NotNull @Min(0) @Max(100000) private Integer sortOrder;
}
