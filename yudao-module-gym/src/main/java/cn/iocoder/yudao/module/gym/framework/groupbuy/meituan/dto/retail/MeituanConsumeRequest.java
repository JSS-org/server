package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeituanConsumeRequest extends MeituanAccountRequest {
    @NotBlank private String receiptCode;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer count;

    @NotBlank private String requestId;
    private String orderId;
    private String thirdDeviceId;
    private String thirdDeviceLocation;
    private String thirdDeviceType;
    private String thirdDeviceCity;
    private String thirdVenueId;
    private String thirdLocationType;
}
