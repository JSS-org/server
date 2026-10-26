package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.List;

public final class GymMobileWorkMembersVO {
    private GymMobileWorkMembersVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MemberPage extends PageParam {
        @Positive private Long storeId;

        @Size(max = 100)
        private String keyword;

        @Pattern(regexp = "all|prospect|private_course|card_expiring|course_expiring")
        private String segment;

        @Min(1)
        @Max(365)
        private Integer expireWithinDays;

        @Positive private Long coachId;
    }

    public record WorkMember(
            String id,
            String name,
            String maskedPhone,
            String customerType,
            String storeId,
            List<String> coachIds,
            CardSummary cardSummary,
            CourseSummary courseSummary,
            int assignmentVersion) {}

    public record CardSummary(
            int activeCount,
            int durationCount,
            int timesCount,
            long remainingUses,
            long reservedUses,
            long availableUses,
            String nearestExpiresAt) {}

    public record CourseSummary(
            int activeCount,
            long remainingLessons,
            long reservedLessons,
            long availableLessons,
            String nearestExpiresAt) {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class FollowupPage extends PageParam {
        @NotNull @Positive private Long storeId;
    }

    @Data
    public static class FollowupCreate {
        @NotNull @Positive private Long storeId;

        @NotBlank
        @Size(max = 2000)
        private String content;

        private Instant nextContactAt;

        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw exception(MOBILE_REQUEST_INVALID);
        }
    }

    @Data
    public static class Assignment {
        @NotNull @Positive private Long storeId;

        @NotNull
        @Size(max = 20)
        private List<@NotNull @Positive Long> employeeIds;

        @NotNull
        @Min(0)
        private Integer expectedVersion;

        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw exception(MOBILE_REQUEST_INVALID);
        }
    }

    public record Followup(
            String id,
            String memberId,
            String storeId,
            String employeeId,
            String content,
            String nextContactAt,
            String createdAt) {}

    public record AssignmentResult(
            String memberId, String storeId, List<String> employeeIds, int version) {}
}
