package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePublicVO.Media;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

public final class GymMobileCoachVO {
    private GymMobileCoachVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileCoachPage")
    public static class CoachPage extends PageParam {
        @NotNull @Positive private Long storeId;
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicCoach")
    public record Coach(
            String id,
            String name,
            Media avatar,
            String introduction,
            List<String> specialties,
            List<Media> qualificationFiles) {}
}
