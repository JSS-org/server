package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.StrictRequest;

import jakarta.validation.constraints.*;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = false)
public class GymMobileCardIssueVO extends StrictRequest {
    @NotNull @Positive private Long storeId;
    @NotNull @Positive private Long memberId;

    @NotNull
    @Pattern(regexp = "card|course")
    private String productType;

    @NotNull @Positive private Long productId;

    @NotBlank
    @Size(max = 2000)
    private String reason;

    @NotNull @Positive private Long approvalTemplateId;
}
