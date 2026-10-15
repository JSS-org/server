package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data public class GymRefundReviewDecisionReqVO {
 @NotNull @Pattern(regexp="retain|stop_selected") private String decision;
 @NotBlank @Size(max=2000) private String note;
 @NotNull @Size(max=99) private List<@NotNull @Positive Long> memberCardIds=List.of();
}
