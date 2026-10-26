package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.*;

public final class GymMobileWorkCoursesVO {
    private GymMobileWorkCoursesVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Dates extends PageParam {
        @Positive private Long storeId;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;

        public Instant from() {
            return startDate.atStartOfDay(GymDateRange.ZONE).toInstant();
        }

        public Instant to() {
            return endDate.atStartOfDay(GymDateRange.ZONE).toInstant();
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileWorkBookingPage")
    public static class BookingPage extends Dates {
        @Positive private Long coachId;

        @Pattern(regexp = "confirmed|cancelled|completed|no_show")
        private String status;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileWorkCheckinPage")
    public static class CheckinPage extends Dates {
        @Positive private Long memberId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileWorkLessonPage")
    public static class LessonPage extends Dates {
        @Positive private Long coachId;
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileWorkBooking")
    public record WorkBooking(
            String id,
            String sessionId,
            String coursePackId,
            String storeId,
            String coachId,
            String startsAt,
            String endsAt,
            String status,
            boolean checkedIn,
            String cancelDeadline,
            String lessonRecordId,
            String memberId,
            String memberName) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileWorkCheckin")
    public record WorkCheckin(
            String id,
            String storeId,
            String enteredAt,
            String exitedAt,
            String source,
            String bookingId,
            String memberId,
            String memberName,
            String coachId) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileWorkLessonRecord")
    public record WorkLessonRecord(
            String id,
            String bookingId,
            String checkinId,
            String confirmedAt,
            String source,
            int deductedLessons,
            Integer remainingLessons,
            String memberId,
            String memberName,
            String coachId,
            String storeId) {}
}
