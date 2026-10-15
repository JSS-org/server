package cn.iocoder.yudao.module.gym.controller.admin.content.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data public class GymQrCodeReqVO {
 @NotNull @Positive private Long storeId;
 @NotNull @Pattern(regexp="miniapp|app") private String platform;
 @NotNull @Pattern(regexp="store|course") private String sceneType;
 @Positive private Long sceneId;
}
