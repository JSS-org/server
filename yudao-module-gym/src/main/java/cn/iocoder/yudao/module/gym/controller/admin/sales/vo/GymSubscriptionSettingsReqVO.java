package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
@Data
public class GymSubscriptionSettingsReqVO {
 @NotNull @Size(max=100) private List<@NotNull @Valid Item> items;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymSubscriptionSettingsReqVOItem")
 public static class Item {
  @NotNull @Positive private Long cardTemplateId;
  @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) private BigDecimal price;
  @Min(1) @Max(28) private Integer billingDay;
  @NotNull @Min(0) @Max(30) private Integer renewalNoticeDays;
  @NotNull @Min(0) @Max(30) private Integer graceDays;
  @NotNull private Boolean enabled;
  @NotNull private Boolean contractRequired;
  @NotNull private Boolean approvalRequired;
 }
}
