package cn.iocoder.yudao.module.gym.controller.admin.member.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.util.List;

@Data
public class GymMemberCoachesReqVO {
    @NotNull
    @Size(max = 20)
    private List<@NotNull @Positive Long> employeeIds;
}
