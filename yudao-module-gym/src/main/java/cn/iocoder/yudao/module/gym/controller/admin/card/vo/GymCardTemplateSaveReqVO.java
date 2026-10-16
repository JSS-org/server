package cn.iocoder.yudao.module.gym.controller.admin.card.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class GymCardTemplateSaveReqVO {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @Pattern(regexp = "period|count")
    private String kind;

    @Min(1)
    @Max(36500)
    private Integer durationDays;

    @Min(1)
    @Max(100000)
    private Integer includedUses;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @Size(max = 100)
    private String memberPrefix;

    @NotNull
    @Pattern(regexp = "all|selected")
    private String accessScope;

    @Size(max = 1000)
    private List<@NotNull @Positive Long> storeIds;

    @NotNull
    @Pattern(regexp = "draft|published|inactive")
    private String status;
}
