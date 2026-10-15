package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
@Data
public class GymOnlineSalesSettingsReqVO {
 @NotNull @Size(max=100) private List<@NotNull @Valid Offer> offers;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymOnlineSalesSettingsReqVOOffer")
 public static class Offer {
  @NotNull @Positive private Long cardTemplateId;
  @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) private BigDecimal salePrice;
  @NotNull private Boolean enabled;
  @NotNull private Boolean contractRequired;
  @NotNull private Boolean approvalRequired;
  private Instant startsAt;
  private Instant endsAt;
 }
}
