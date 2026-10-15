package cn.iocoder.yudao.module.gym.controller.admin.approval.vo;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
@Data
public class GymApprovalTemplateSaveReqVO {
    @NotBlank @Size(max=100) private String name;
    @NotNull @Pattern(regexp="card_opening|contract|price_change|other_configured") private String businessType;
    @NotNull @Pattern(regexp="draft|published|inactive") private String status;
    @NotEmpty @Size(max=20) private List<@NotNull @Valid Step> steps;
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymApprovalTemplateSaveReqVOStep")
 public static class Step {
        @NotNull @Min(1) @Max(20) private Integer stepNo;
        @NotNull @Positive private Long approverEmployeeId;
    }
}
