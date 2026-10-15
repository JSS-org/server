package cn.iocoder.yudao.module.gym.controller.admin.content.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
@Data @EqualsAndHashCode(callSuper=true) public class GymQrCodePageReqVO extends GymBusinessPageReqVO {
 @Pattern(regexp="miniapp|app") private String platform;
 @Pattern(regexp="store|course") private String sceneType;
}
