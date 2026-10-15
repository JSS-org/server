package cn.iocoder.yudao.module.gym.controller.admin.contract.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data public class GymContractReminderReqVO {
 @NotNull @Pattern(regexp="sms|miniapp|app") private String channel;
}
