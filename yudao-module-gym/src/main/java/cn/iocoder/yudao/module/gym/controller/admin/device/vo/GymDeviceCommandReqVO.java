package cn.iocoder.yudao.module.gym.controller.admin.device.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data public class GymDeviceCommandReqVO {
 @NotNull @Pattern(regexp="open_door|open_locker") private String command;
 @Positive private Long compartmentId;
 @NotBlank @Size(max=500) private String reason;
}
