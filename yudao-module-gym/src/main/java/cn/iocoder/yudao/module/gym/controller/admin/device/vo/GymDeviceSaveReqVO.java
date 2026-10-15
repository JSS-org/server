package cn.iocoder.yudao.module.gym.controller.admin.device.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.Instant;
@Data public class GymDeviceSaveReqVO {
 @NotNull @Positive private Long storeId;
 @NotBlank @Size(max=100) private String name;
 @NotNull @Pattern(regexp="door|locker|camera|face_reader") private String type;
 @NotBlank @Size(max=64) private String provider;
 @NotBlank @Size(max=128) private String deviceUid;
 @Size(max=200) private String location;
 @Pattern(regexp="active|inactive") private String status;
 private Boolean online;private Instant lastSeenAt;
}
