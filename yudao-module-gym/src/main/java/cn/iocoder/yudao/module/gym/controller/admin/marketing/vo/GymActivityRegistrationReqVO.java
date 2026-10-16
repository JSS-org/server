package cn.iocoder.yudao.module.gym.controller.admin.marketing.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class GymActivityRegistrationReqVO {
    @NotNull @Positive private Long memberId;
    @NotNull @Positive private Long storeId;
}
