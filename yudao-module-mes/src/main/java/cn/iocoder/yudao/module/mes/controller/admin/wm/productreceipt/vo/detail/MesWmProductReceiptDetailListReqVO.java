package cn.iocoder.yudao.module.mes.controller.admin.wm.productreceipt.vo.detail;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Schema(description = "管理后台 - MES 产品收货单明细列表 Request VO")
@Data
public class MesWmProductReceiptDetailListReqVO {

    @Schema(description = "收货单编号，与行编号至少提供一个", example = "1024")
    @Positive private Long receiptId;

    @Schema(description = "收货单行编号，提供两个编号时同时筛选", example = "1025")
    @Positive private Long lineId;

    @AssertTrue(message = "收货单编号与行编号至少提供一个")
    public boolean isParentSpecified() {
        return receiptId != null || lineId != null;
    }
}
