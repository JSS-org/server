package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeituanRefundAuditRequest extends MeituanOperatorRequest {
    @NotBlank private String sequenceId;
    @NotBlank private String orderId;
    @NotBlank private String refundId;

    @NotNull
    @Min(1)
    @Max(2)
    private Integer auditResult;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String operatorName;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String operatorIp;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String operatorMobile;

    private String operatorRemark;
    @NotNull private Boolean refundFeeType;

    @NotNull
    @Min(0)
    private Long refundAmount;
}
