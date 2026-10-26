package cn.iocoder.yudao.module.gym.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.util.List;

@Data
@Schema(description = "明确发布到全部当前营业任职门店的教练资料；整个对象替换，省略对象保持原值")
public class GymPublicCoachProfileVO {
    @NotNull private Boolean published;
    @Positive private Long avatarFileId;

    @NotNull
    @Size(max = 4000)
    private String introduction;

    @NotNull
    @Size(max = 20)
    private List<@NotBlank @Size(max = 80) String> specialties;

    @NotNull
    @Size(max = 20)
    private List<@NotNull @Positive Long> qualificationFileIds;
}
