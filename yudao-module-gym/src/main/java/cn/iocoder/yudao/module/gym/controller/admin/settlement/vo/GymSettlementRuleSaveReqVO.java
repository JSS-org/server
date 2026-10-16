package cn.iocoder.yudao.module.gym.controller.admin.settlement.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class GymSettlementRuleSaveReqVO {
    @NotNull
    @Pattern(regexp = "day|week|month")
    private String cycleUnit;

    @NotNull
    @Min(1)
    @Max(365)
    private Integer cycleValue;

    @NotNull private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean effectiveToPresent;

    public void setEffectiveTo(LocalDate value) {
        effectiveTo = value;
        effectiveToPresent = true;
    }

    @Pattern(regexp = "draft|active")
    private String status;

    @NotNull
    @Size(min = 1, max = 20)
    private List<@NotNull @Valid Item> items;

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymSettlementRuleSaveReqVOItem")
    public static class Item {
        @NotNull @Positive private Long paymentAccountId;

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("1")
        @Digits(integer = 1, fraction = 6)
        private BigDecimal ratio;
    }
}
