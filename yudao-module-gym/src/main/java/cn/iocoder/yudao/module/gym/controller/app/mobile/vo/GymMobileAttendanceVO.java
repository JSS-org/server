package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

public final class GymMobileAttendanceVO {
    private GymMobileAttendanceVO() {}

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileAttendanceMonth")
    public static class Request {
        @NotNull @Positive private Long storeId;

        @NotBlank
        @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])")
        private String month;
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileAttendanceSummary")
    public record Summary(
            long checkinCount,
            int activeDays,
            Long rank,
            long rankingPopulation,
            String updatedAt) {}
}
