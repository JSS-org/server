package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Positive;

import lombok.Data;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

public final class GymMobileDashboardVO {
    private GymMobileDashboardVO() {}

    @Data
    public static class Query {
        @Positive private Long storeId;

        @Schema(description = "上海自然日，默认今天，不允许未来日期；成员总量和待办仍为当前值")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Scope(String type, List<String> storeIds, String employeeId) {}

    public record Performance(String todayAmount, String monthAmount) {}

    public record Checkins(long todayCount, long yesterdayCount) {}

    public record Lessons(long todayCount, long yesterdayCount, long monthCount) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Members(long newTodayCount, Long responsibleCount, Long storeTotalCount) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Dashboard(
            Scope scope,
            String date,
            Performance performance,
            Checkins checkins,
            Lessons lessons,
            Members members,
            Long pendingApprovalCount,
            String updatedAt) {}
}
