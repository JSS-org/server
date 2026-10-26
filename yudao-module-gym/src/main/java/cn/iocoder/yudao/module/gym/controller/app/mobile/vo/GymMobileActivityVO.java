package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.StrictRequest;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePublicVO.Media;

import jakarta.validation.constraints.*;

import lombok.*;

import java.time.Instant;
import java.util.List;

public final class GymMobileActivityVO {
    private GymMobileActivityVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileActivityPage")
    public static class ActivityPage extends PageParam {
        @NotNull @Positive private Long storeId;

        @Pattern(regexp = "open|ended")
        private String status;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileActivityRegistrationPage")
    public static class RegistrationPage extends PageParam {
        @Pattern(regexp = "registered|cancelled")
        private String status;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileActivityCreate")
    public static class Create extends StrictRequest {
        @NotNull @Positive private Long storeId;

        @NotBlank
        @Size(max = 100)
        private String title;

        @NotBlank
        @Size(max = 4000)
        private String description;

        @Positive private Long coverFileId;
        @NotNull private Instant startsAt;
        @NotNull private Instant endsAt;
        @NotNull private Instant registrationDeadline;
        private Instant cancelDeadline;

        @NotNull
        @Min(1)
        @Max(100000)
        private Integer capacity;
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileActivity")
    public record Activity(
            String id,
            String storeId,
            List<String> storeIds,
            String title,
            String description,
            Media cover,
            String startsAt,
            String endsAt,
            String registrationDeadline,
            String cancelDeadline,
            Integer capacity,
            long registeredCount,
            String status) {}

    public record Registration(String id, String activityId, String status, String registeredAt) {}

    public record History(String id, Activity activity, String status, String registeredAt) {}

    public record Cancelled(String status) {}
}
