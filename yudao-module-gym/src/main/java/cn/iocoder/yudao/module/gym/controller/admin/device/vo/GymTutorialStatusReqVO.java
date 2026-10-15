package cn.iocoder.yudao.module.gym.controller.admin.device.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data public class GymTutorialStatusReqVO {@NotNull @Pattern(regexp="active|inactive") private String status;}
