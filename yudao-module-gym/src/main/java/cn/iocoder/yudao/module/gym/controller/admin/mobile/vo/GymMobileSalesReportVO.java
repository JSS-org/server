package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.*;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

public final class GymMobileSalesReportVO {
    private GymMobileSalesReportVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @Positive private Long storeId;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;

        @Pattern(regexp = "card|course")
        private String productType;

        @Positive private Long employeeId;
    }

    public record SaleRow(
            String id,
            String orderNo,
            String storeId,
            String employeeId,
            String productType,
            String productName,
            String paidAmount,
            String refundedAmount,
            String netAmount,
            String paidAt) {}

    public record Summary(
            String paidAmount,
            String refundedAmount,
            String netAmount,
            long paidOrderCount,
            String cardAmount,
            long cardOrderCount,
            String cardAverageOrderAmount,
            String courseAmount,
            long courseOrderCount,
            String courseAverageOrderAmount) {}

    public record Result(List<SaleRow> list, long total, Summary summary) {}
}
