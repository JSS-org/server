package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.*;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

public final class GymMobilePerformanceVO {
    private GymMobilePerformanceVO() {}

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

        @NotBlank
        @Pattern(regexp = "employee|store")
        private String groupBy;
    }

    public record Row(
            String storeId,
            String employeeId,
            String name,
            Long rank,
            String netAmount,
            long paidOrderCount,
            long lessonCount,
            String averagePerDayAmount) {}

    public record Summary(
            String netAmount,
            long employeeCount,
            long storeCount,
            String averagePerEmployeeAmount,
            String averagePerStoreAmount,
            long lessonCount) {}

    public record Result(List<Row> list, long total, Summary summary) {}
}
