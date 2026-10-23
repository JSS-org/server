package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MeituanRefundPrecheckRequest extends MeituanOperatorRequest {
    private String orderId;
    private List<@NotBlank String> serialNumbers;
    private String sequenceId;

    @AssertTrue(message = "orderId 和 serialNumbers 至少填写一个")
    @JsonIgnore
    public boolean isRefundTargetPresent() {
        return orderId != null && !orderId.isBlank()
                || serialNumbers != null && !serialNumbers.isEmpty();
    }
}
