package cn.iocoder.yudao.module.pay.controller.admin.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 支付应用精简信息 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayAppSimpleRespVO {
    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "应用名", requiredMode = Schema.RequiredMode.REQUIRED, example = "商城")
    private String name;
}
