package cn.iocoder.yudao.module.gym.controller.admin.settlement.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data
public class GymPaymentAccountPatchReqVO {
 @Size(max=100) private String accountName;
 @Size(max=128) @Pattern(regexp="|(?:vault|secret|kms)://[A-Za-z0-9][A-Za-z0-9_./@:-]*",message="请填写vault://、secret://或kms://安全存储引用，不接受密钥或证书内容") private String credentialRef;
 @com.fasterxml.jackson.annotation.JsonIgnore private boolean credentialRefPresent;
 public void setCredentialRef(String value){credentialRef=value;credentialRefPresent=true;}
 private Long storeId;
 @com.fasterxml.jackson.annotation.JsonIgnore private boolean storeIdPresent;
 public void setStoreId(Long value){storeId=value;storeIdPresent=true;}
 private String provider; private String merchantId; private String lastFour;
 private GymPaymentAccountCreateReqVO.Details accountDetails;
 private String bindingStatus;
}
