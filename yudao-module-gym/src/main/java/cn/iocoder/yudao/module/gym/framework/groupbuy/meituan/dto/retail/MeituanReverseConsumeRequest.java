package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeituanReverseConsumeRequest extends MeituanAccountRequest {
    @NotBlank private String receiptCode;
    @NotBlank private String dealId;
}
