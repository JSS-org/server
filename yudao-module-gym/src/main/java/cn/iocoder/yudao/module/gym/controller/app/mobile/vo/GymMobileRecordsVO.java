package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import java.math.BigDecimal;

/** Explicit public projections: never return persistence objects or internal signing evidence. */
public final class GymMobileRecordsVO {
    private GymMobileRecordsVO() {}

    public record Measurement(
            String id,
            String storeId,
            String measuredAt,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal bodyFatPercent,
            BigDecimal muscleMassKg,
            String note,
            String reportFileId,
            String coachId) {}

    public record TrainingPlan(
            String id,
            String storeId,
            String title,
            String content,
            String fileId,
            String status,
            String coachId,
            String createdAt,
            String updatedAt) {}

    public record Contract(
            String id,
            String title,
            String storeId,
            String status,
            String issuedAt,
            String signedAt,
            String originalFileId,
            String signedFileId,
            String agreementVersion) {}

    public record Checkin(
            String id,
            String storeId,
            String enteredAt,
            String exitedAt,
            String source,
            String bookingId) {}

    public record FitnessSummary(
            Measurement latestMeasurement,
            long activePlanCount,
            long totalCheckins,
            long totalLessons) {}
}
