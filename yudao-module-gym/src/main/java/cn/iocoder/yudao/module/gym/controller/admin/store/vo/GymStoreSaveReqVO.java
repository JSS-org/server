package cn.iocoder.yudao.module.gym.controller.admin.store.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "门店资料；PATCH 省略或 null 字段保持原值，businessHours 整体替换")
public class GymStoreSaveReqVO {
    @Schema(description = "仅新增可选：绑定已有 system_dept，否则自动新建部门")
    @Positive
    private Long deptId;

    @Schema(description = "仅新建部门使用：父部门，缺省为根")
    @Positive
    private Long parentDeptId;

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 30)
    private String name;

    @Size(max = 2000)
    private String description;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal areaSqm;

    @Size(max = 30)
    private List<@NotBlank @Size(max = 50) String> features;

    @NotBlank
    @Size(max = 500)
    private String address;

    @DecimalMin("-180")
    @DecimalMax("180")
    @Digits(integer = 3, fraction = 7)
    private BigDecimal longitude;

    @DecimalMin("-90")
    @DecimalMax("90")
    @Digits(integer = 2, fraction = 7)
    private BigDecimal latitude;

    @Size(max = 32)
    private String servicePhone;

    @Size(max = 64)
    private String serviceWechat;

    @NotNull @Valid private BusinessHours businessHours;

    @NotBlank
    @Pattern(regexp = "active|inactive")
    private String status;

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymStoreSaveReqVOBusinessHours")
    public static class BusinessHours {
        @NotBlank
        @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d")
        private String opens;

        @NotBlank
        @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d")
        private String closes;
    }
}
