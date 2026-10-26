package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePublicVO.StoreBrief;

import java.util.List;

@io.swagger.v3.oas.annotations.media.Schema(name = "GymMobileWorkContext")
public record GymMobileWorkContextVO(
        String employeeId, List<String> roles, List<String> permissions, List<StoreBrief> stores) {}
