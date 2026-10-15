package cn.iocoder.yudao.module.gym.controller.admin.contract.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data
public class GymContractTemplateSaveReqVO {
 @NotBlank @Size(max=100) private String name;
 @NotNull @Pattern(regexp="card|course") private String applicableType;
 @NotNull @Min(0) @Max(365) private Integer reminderDays;
 @NotNull @Positive private Long fileId;
 @NotNull @Pattern(regexp="draft|published|inactive") private String status;
}
