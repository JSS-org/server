package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MeituanBatchConsumeRequest extends MeituanAccountRequest {
    @NotBlank private String requestId;
    @Valid @NotEmpty private List<@NotNull ReceiptCodeInfo> receiptCodeInfos;

    @Getter
    @Setter
    public static class ReceiptCodeInfo {
        @NotBlank private String receiptCode;

        @NotBlank
        @Pattern(regexp = "(?:[1-9]|[1-9][0-9]|100)")
        @Schema(description = "平台字段类型为 string，数量 1 至 100")
        private String count;
    }
}
