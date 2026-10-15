package cn.iocoder.yudao.module.gym.controller.admin.marketing.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data public class GymFranchisePatchReqVO {
 @NotNull @Pattern(regexp="new|contacted|closed") private String status;
}
