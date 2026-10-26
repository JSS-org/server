package cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "服务零售请求；storeId 为本地门店，biz 为类型化平台业务参数")
public class GymMeituanRetailReqVO<T> {
    @NotNull @Positive private Long storeId;
    @Valid @NotNull private T biz;
}
