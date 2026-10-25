package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.StrictRequest;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePublicVO.Media;

import jakarta.validation.constraints.*;

import lombok.*;

import java.util.List;

public final class GymMobileEquipmentVO {
    private GymMobileEquipmentVO() {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Resolve extends StrictRequest {
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{32}")
        private String scene;
    }

    public record Resolution(String type, String equipmentId, String storeId) {}

    public record Scene(String equipmentId, String scene) {}

    public record Tutorial(String id, String title, String content, Media image, Media video) {}

    public record Tutorials(String equipmentId, String name, List<Tutorial> tutorials) {}
}
