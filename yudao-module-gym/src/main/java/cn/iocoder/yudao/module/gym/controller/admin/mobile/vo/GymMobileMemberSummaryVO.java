package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import lombok.Data;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public final class GymMobileMemberSummaryVO {
    private GymMobileMemberSummaryVO() {}

    @Data
    public static class Query {
        @Positive private Long storeId;

        @Schema(description = "用于筛选当前可见会员的注册自然日（Asia/Shanghai，1000～9998年），其余指标按当前时间统计")
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;
    }

    public record Summary(
            long memberCount,
            long privateCourseMemberCount,
            long newMemberCount,
            long expiringCardCount,
            long expiringCourseCount,
            @Schema(description = "请求中的注册日期筛选值") String date,
            @Schema(description = "当前人口与权益统计时点，包含时区偏移；不是历史快照日期") String updatedAt) {}
}
