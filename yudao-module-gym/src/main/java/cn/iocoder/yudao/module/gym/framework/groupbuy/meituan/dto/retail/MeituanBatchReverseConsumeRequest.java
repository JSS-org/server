package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MeituanBatchReverseConsumeRequest extends MeituanAccountRequest {
    @NotEmpty private List<@NotBlank String> receiptCodeList;
    @NotBlank private String dealId;
}
