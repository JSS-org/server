package cn.iocoder.yudao.module.gym.controller.admin.device.vo;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
@Data public class GymCompartmentsReqVO {
 @NotNull @Size(max=500) private List<@NotNull @Valid Item> compartments;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymCompartmentsReqVOItem")
 public static class Item {
  @NotBlank @Size(max=32) private String number;
  @NotNull @Pattern(regexp="available|inactive") private String status;
 }
}
