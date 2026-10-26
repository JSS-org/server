package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.*;

import java.util.List;

public final class GymMobileNotificationsVO {
    private GymMobileNotificationsVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @Pattern(regexp = "booking|contract|member_card|course_pack|activity")
        private String type;

        private Boolean unreadOnly;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Preferences extends GymMobileBookingVO.StrictRequest {
        @NotNull
        @Size(min = 1, max = 4)
        private List<@NotNull @Pattern(regexp = "booking|contract|entitlement|activity") String>
                templateKeys;

        @NotNull private Boolean enabled;
    }

    public record Notification(
            String id,
            String type,
            String title,
            String content,
            String businessId,
            String storeId,
            String createdAt,
            String readAt) {}

    public record Saved(boolean saved) {}
}
