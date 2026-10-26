package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

public final class GymMobileDevicesVO {
    private GymMobileDevicesVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @NotNull @Positive private Long storeId;

        @Pattern(regexp = "door|locker")
        private String type;
    }

    /** Administrative configuration state, not live device telemetry. */
    public record Device(String id, String name, String type, String status) {}

    /** Configured availability, not occupancy or a successful hardware operation. */
    public record Compartment(String id, String number, String status) {}
}
