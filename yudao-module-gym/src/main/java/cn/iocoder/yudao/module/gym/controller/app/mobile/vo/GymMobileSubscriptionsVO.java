package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.Positive;

import lombok.*;

public final class GymMobileSubscriptionsVO {
    private GymMobileSubscriptionsVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @Positive private Long storeId;
    }

    public record Subscription(
            String id,
            String storeId,
            String productId,
            String status,
            String cycleAmount,
            String nextChargeAt,
            String agreementVersion,
            String terminatedAt) {}
}
