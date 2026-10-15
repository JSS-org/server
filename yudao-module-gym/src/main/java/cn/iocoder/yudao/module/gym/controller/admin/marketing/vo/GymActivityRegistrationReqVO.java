package cn.iocoder.yudao.module.gym.controller.admin.marketing.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data public class GymActivityRegistrationReqVO {
 @NotNull @Positive private Long memberId;
 @NotNull @Positive private Long storeId;
}
