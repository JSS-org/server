package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeituanOperatorRequest {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "服务端填入当前登录员工 ID", accessMode = Schema.AccessMode.READ_ONLY)
    @Size(min = 1, max = 32)
    private String operatorId;
}
