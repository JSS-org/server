package cn.iocoder.yudao.module.gym.controller.admin.approval.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class GymApprovalCreateReqVO {
    @NotNull @Positive private Long storeId;
    @NotNull @Positive private Long templateId;

    @NotNull
    @Pattern(regexp = "card_opening|contract|price_change|other_configured")
    private String businessType;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String reason;

    @Size(max = 32)
    private String sourceType;

    @Positive private Long sourceId;
    @Positive private Long memberId;
    @Positive private Long cardTemplateId;

    @DecimalMin("0")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal requestedPrice;

    @Size(max = 10)
    private List<@NotNull @Positive Long> attachmentIds;
}
