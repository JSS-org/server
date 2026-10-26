package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.StrictRequest;

import jakarta.validation.constraints.*;

import lombok.*;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = false)
public class GymMobileCoursePriceVO extends StrictRequest {
    @NotNull @Positive private Long storeId;
    @NotNull @Positive private Long productId;
    @Positive private Long memberId;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal proposedAmount;

    @NotBlank
    @Size(max = 2000)
    private String reason;

    @NotNull @Positive private Long approvalTemplateId;
}
