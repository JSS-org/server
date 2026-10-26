package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.*;

public class GymMobilePointProductsVO {
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @NotNull @Positive private Long storeId;
    }

    public record Product(
            String id, String name, long requiredPoints, Integer stock, String cardProductId) {}
}
