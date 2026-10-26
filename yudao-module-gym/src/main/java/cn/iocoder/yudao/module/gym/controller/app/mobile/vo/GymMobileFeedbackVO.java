package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.StrictRequest;

import jakarta.validation.constraints.*;

import lombok.*;

import java.util.List;

public final class GymMobileFeedbackVO {
    private GymMobileFeedbackVO() {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileFeedbackSubmit")
    public static class Submit extends StrictRequest {
        @NotNull @Positive private Long storeId;

        @NotBlank
        @Pattern(regexp = "feedback|review")
        private String type;

        @NotBlank
        @Size(max = 2000)
        private String content;

        @Min(1)
        @Max(5)
        private Integer rating;

        @Size(max = 9)
        private List<@NotNull @Positive Long> fileIds;

        private Boolean allowCardContext;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileOwnFeedbackPage")
    public static class OwnPage extends PageParam {
        @Positive private Long storeId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileStoreFeedbackPage")
    public static class StorePage extends PageParam {
        @NotNull @Positive private Long storeId;

        @Pattern(regexp = "feedback|review")
        private String type;

        @Pattern(regexp = "received|replied")
        private String status;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileFeedbackReply")
    public static class Reply extends StrictRequest {
        @NotBlank
        @Size(max = 2000)
        private String content;
    }

    public record Receipt(String id, String status, String createdAt) {}

    public record OwnFeedback(
            String id,
            String type,
            String content,
            Integer rating,
            String status,
            String reply,
            String createdAt) {}

    public record CardContext(long activeCardCount, long activeCoursePackCount) {}

    public record StoreFeedback(
            String id,
            String type,
            Integer rating,
            String content,
            List<String> fileIds,
            String status,
            String createdAt,
            CardContext cardContext,
            String reply) {}

    public record Replied(String id, String status, String reply) {}
}
