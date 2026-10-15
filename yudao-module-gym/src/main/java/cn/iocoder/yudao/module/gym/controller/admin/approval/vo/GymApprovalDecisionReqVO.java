package cn.iocoder.yudao.module.gym.controller.admin.approval.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data
public class GymApprovalDecisionReqVO {
 @NotNull @Pattern(regexp="approve|reject") private String decision;
 @Size(max=2000) private String comment;
}
