package cn.iocoder.yudao.module.gym.controller.admin.settlement.vo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class GymPaymentAccountCreateReqVO {
 @NotBlank @Size(max=100) private String accountName;
 @Positive private Long storeId;
 @NotNull @Pattern(regexp="wechat|alipay|bank") private String provider;
 @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String merchantId;
 @NotNull @Valid private Details accountDetails;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymPaymentAccountCreateReqVODetails")
 public static class Details {
  @NotNull @Pattern(regexp="[0-9]{4}") private String lastFour;
  @Size(max=128) @Pattern(regexp="|(?:vault|secret|kms)://[A-Za-z0-9][A-Za-z0-9_./@:-]*",message="请填写vault://、secret://或kms://安全存储引用，不接受密钥或证书内容") private String credentialRef;
 }
}
