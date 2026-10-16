package cn.iocoder.yudao.module.gym.controller.admin.content.vo;

import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GymQrCodePageReqVO extends GymBusinessPageReqVO {
    @Pattern(regexp = "miniapp|app")
    private String platform;

    @Pattern(regexp = "store|course")
    private String sceneType;
}
