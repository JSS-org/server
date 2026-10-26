package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MeituanRefundApplyRequest extends MeituanRefundPrecheckRequest {
    private List<@NotBlank String> receiptList;
    private Boolean refundFeeType;
    @Positive private Long refundAmount;
    private String refundReason;

    @Override
    @NotBlank
    @Schema(description = "本系统要求显式流水号，重试沿用原值，不自动生成")
    public String getSequenceId() {
        return super.getSequenceId();
    }

    @AssertTrue(message = "部分退款须提供正数 refundAmount")
    @JsonIgnore
    public boolean isPartialRefundAmountValid() {
        return !Boolean.TRUE.equals(refundFeeType) || refundAmount != null && refundAmount > 0;
    }
}
