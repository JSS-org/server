package cn.iocoder.yudao.module.trade.controller.admin.delivery.vo.expresstemplate;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryExpressChargeModeEnum;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 快递运费模板 Base VO，提供给添加、修改、详细的子 VO 使用
 * 如果子 VO 存在差异的字段，请不要添加到这里，影响 Swagger 文档生成
 */
@Data
public class DeliveryExpressTemplateBaseVO {

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "王五")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称不能超过128字")
    private String name;

    @Schema(description = "配送计费方式 1:按件 2:按重量 3:按体积", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "配送计费方式 1:按件 2:按重量 3:按体积不能为空")
    @InEnum(DeliveryExpressChargeModeEnum.class)
    private Integer chargeMode;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "排序不能为空")
    @Min(value = 0, message = "排序不能为负数")
    private Integer sort;
}
