package cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 无平台业务参数的查询只提供本地门店 ID。 */
@Getter
@Setter
public class GymMeituanStoreReqVO {
    @NotNull @Positive private Long storeId;
}
