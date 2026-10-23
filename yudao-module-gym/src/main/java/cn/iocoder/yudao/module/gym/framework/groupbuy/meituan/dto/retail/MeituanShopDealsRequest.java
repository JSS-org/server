package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeituanShopDealsRequest {
    @NotNull
    @Min(1)
    @Schema(description = "页码，从 1 开始；不是记录偏移量")
    private Integer offset = 1;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer limit = 100;

    @NotNull
    @Min(1)
    @Max(2)
    private Integer source = 1;
}
