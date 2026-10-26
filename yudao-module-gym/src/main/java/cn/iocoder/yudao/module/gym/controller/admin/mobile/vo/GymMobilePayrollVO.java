package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.*;

import java.util.List;

public final class GymMobilePayrollVO {
    private GymMobilePayrollVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileOwnPayrollPage")
    public static class OwnPage extends PageParam {
        @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])")
        private String month;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileStorePayrollPage")
    public static class StorePage extends PageParam {
        @NotNull @Positive private Long storeId;

        @NotBlank
        @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])")
        private String month;

        @Positive private Long employeeId;
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePayrollItem")
    public record Item(String name, String amount, String description) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePayroll")
    public record Payroll(
            String id,
            String storeId,
            String employeeId,
            String month,
            String status,
            String baseAmount,
            String commissionAmount,
            String bonusAmount,
            String deductionAmount,
            String totalAmount,
            List<Item> items) {}
}
