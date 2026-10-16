package cn.iocoder.yudao.module.gym.controller.admin.salary.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class GymSalaryTemplateSaveReqVO {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal baseSalary;

    @NotNull private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean effectiveToPresent;

    public void setEffectiveTo(LocalDate value) {
        effectiveTo = value;
        effectiveToPresent = true;
    }

    @Pattern(regexp = "draft|published")
    private String status;

    @NotNull
    @Size(max = 100)
    private List<@NotNull @Valid Rule> rules;

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymSalaryTemplateSaveReqVORule")
    public static class Rule {
        @NotBlank
        @Pattern(regexp = "card_sales|pt_sales|lessons|allowance|deduction")
        private String component;

        @NotBlank
        @Pattern(regexp = "rate|fixed|per_unit")
        private String calculationType;

        @DecimalMin("0")
        @DecimalMax("1")
        @Digits(integer = 1, fraction = 6)
        private BigDecimal rate;

        @DecimalMin("0")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal fixedAmount;

        @DecimalMin("0")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal unitAmount;

        @DecimalMin("0")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal lowerBound;

        @DecimalMin("0")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal upperBound;
    }
}
