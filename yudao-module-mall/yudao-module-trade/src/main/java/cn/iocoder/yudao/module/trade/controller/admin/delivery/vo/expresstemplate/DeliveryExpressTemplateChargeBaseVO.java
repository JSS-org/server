package cn.iocoder.yudao.module.trade.controller.admin.delivery.vo.expresstemplate;

import cn.iocoder.yudao.module.trade.util.delivery.DeliveryExpressRuleUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.util.List;

/**
 * 快递运费模板运费设置 Base VO，提供给添加运费模板使用
 */
@Data
public class DeliveryExpressTemplateChargeBaseVO {

    @Schema(
            description = "编号",
            example = "6592",
            hidden = true) // 由于想简单一点，复用这个 VO 在更新操作，所以 hidden 为 false
    private Long id;

    @Schema(
            description = "区域编号列表",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[1,120000]")
    @NotEmpty(message = "区域编号列表不能为空")
    private List<Integer> areaIds;

    @Schema(description = "首件数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    @NotNull(message = "首件数量不能为空")
    private Double startCount;

    @Schema(description = "起步价", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "起步价不能为空")
    private Integer startPrice;

    @Schema(description = "续件数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "续件数量不能为空")
    private Double extraCount;

    @Schema(description = "额外价", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")
    @NotNull(message = "额外价不能为空")
    private Integer extraPrice;

    @JsonIgnore
    @AssertTrue(message = "计费数量和金额必须非负且有限，有续费时续件数量必须大于零")
    public boolean isChargeRuleValid() {
        return DeliveryExpressRuleUtils.validCharge(startCount, startPrice, extraCount, extraPrice);
    }
}
