package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

public class GymMobilePointRedemptionVO {
    @Data
    public static class Create {
        @NotNull @Positive private Long storeId;
        @NotNull @Positive private Long productId;
    }

    public record Result(String id, String status, long spentPoints, String memberCardId) {}
}
