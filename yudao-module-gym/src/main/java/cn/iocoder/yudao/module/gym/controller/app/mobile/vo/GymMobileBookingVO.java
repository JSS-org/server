package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public final class GymMobileBookingVO {
    private GymMobileBookingVO() {}

    public abstract static class StrictRequest {
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw exception(MOBILE_REQUEST_INVALID);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Create extends StrictRequest {
        @NotNull @Positive private Long sessionId;
        @NotNull @Positive private Long coursePackId;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Cancel extends StrictRequest {
        @Size(max = 500)
        private String reason;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Sessions extends PageParam {
        @NotNull @Positive private Long storeId;
        @Positive private Long productId;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;
    }

    public record Booking(
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
            String lessonRecordId) {}

    public record LessonRecord(
            String id,
            String bookingId,
            String checkinId,
            String confirmedAt,
            String source,
            int deductedLessons,
            Integer remainingLessons) {}

    public record CourseSession(
            String id,
            String storeId,
            String coachId,
            String productId,
            String startsAt,
            String endsAt,
            int capacity,
            int remainingCapacity,
            boolean bookable,
            String unavailableReason) {}
}
