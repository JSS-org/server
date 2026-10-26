package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

/** 仅用于平台操作记录，不代替本地权限校验；客户端不可指定操作人。 */
@Getter
@Setter
public class MeituanAccountRequest {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "服务端填入商家系统操作账号；后台入口取当前员工 ID", accessMode = Schema.AccessMode.READ_ONLY)
    private String appShopAccount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "服务端填入商家系统操作人名称；后台入口取当前员工昵称", accessMode = Schema.AccessMode.READ_ONLY)
    private String appShopAccountName;
}
