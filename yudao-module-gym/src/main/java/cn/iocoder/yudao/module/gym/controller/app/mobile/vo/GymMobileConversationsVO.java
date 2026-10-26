package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.*;

public final class GymMobileConversationsVO {
    private GymMobileConversationsVO() {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Create extends GymMobileBookingVO.StrictRequest {
        @NotNull @Positive private Long storeId;
        @NotNull @Positive private Long memberId;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Send extends GymMobileBookingVO.StrictRequest {
        @NotBlank
        @Size(max = 2000)
        private String content;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @Positive private Long beforeId;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Read extends GymMobileBookingVO.StrictRequest {
        @NotNull @Positive private Long lastReadMessageId;
    }

    public record Created(String id) {}

    public record Message(
            String id,
            String conversationId,
            String senderType,
            String content,
            String createdAt) {}

    public record Conversation(String id, String storeId, int unreadCount, Message lastMessage) {}

    public record ReadState(String lastReadMessageId, int unreadCount) {}
}
