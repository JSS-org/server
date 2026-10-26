package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileAttendanceVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileAttendanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/me/attendance-summary")
@PreAuthorize("isAuthenticated()")
@Tag(name = "小程序 - 本人月度签到汇总")
public class AppGymAttendanceController {
    @Resource private GymMobileAttendanceService service;

    @GetMapping
    @Operation(summary = "按上海月份统计本人成功签到、活跃天数及本人同店排名")
    public CommonResult<Summary> get(@Valid Request req) {
        return success(service.get(req));
    }
}
