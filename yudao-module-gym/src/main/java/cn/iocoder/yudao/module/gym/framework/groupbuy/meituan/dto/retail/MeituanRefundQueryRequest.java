package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeituanRefundQueryRequest extends MeituanOperatorRequest {
    @NotBlank private String orderId;
    @NotBlank private String refundId;
    @NotBlank private String sequenceId;
}
