package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.StrictRequest;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

public final class GymMobileWorkCourseWriteVO {
    private GymMobileWorkCourseWriteVO() {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileWorkSessionCreate")
    public static class SessionCreate extends StrictRequest {
        @NotNull @Positive private Long storeId;
        @NotNull @Positive private Long coachId;
        @NotNull @Positive private Long productId;
        @NotNull private Instant startsAt;
        @NotNull private Instant endsAt;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileWorkBookingCreate")
    public static class BookingCreate extends StrictRequest {
        @NotNull @Positive private Long memberId;
        @NotNull @Positive private Long sessionId;
        @NotNull @Positive private Long coursePackId;
    }
}
