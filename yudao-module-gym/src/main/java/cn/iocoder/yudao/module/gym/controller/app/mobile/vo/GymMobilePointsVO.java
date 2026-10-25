package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.Pattern;

import lombok.*;

public final class GymMobilePointsVO {
    private GymMobilePointsVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @Pattern(regexp = "earn|spend|refund")
        private String type;
    }

    public record Balance(long balance, long totalEarned, long totalSpent) {}

    public record Record(
            String id, long delta, long balanceAfter, String reason, String createdAt) {}
}
