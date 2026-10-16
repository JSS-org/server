package cn.iocoder.yudao.module.gym.controller.admin.member.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class GymMeasurementSaveReqVO {
    @NotNull @Positive private Long storeId;
    @NotNull @Positive private Long memberId;
    @NotNull private Instant measuredAt;

    @DecimalMin("0.01")
    @DecimalMax("300")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal heightCm;

    @DecimalMin("0.01")
    @DecimalMax("500")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal weightKg;

    @DecimalMin("0")
    @DecimalMax("100")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal bodyFatPercent;

    @DecimalMin("0")
    @DecimalMax("500")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal muscleMassKg;

    @Size(max = 2000)
    private String note;

    @Positive private Long reportFileId;
}
