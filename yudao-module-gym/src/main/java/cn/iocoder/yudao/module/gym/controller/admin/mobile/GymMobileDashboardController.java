package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDashboardVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileDashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile")
@Validated
@PreAuthorize("isAuthenticated()")
@Tag(name = "移动工作台 - 首页概览")
public class GymMobileDashboardController {
    @Resource private GymMobileDashboardService service;

    @GetMapping("/dashboard")
    @Operation(summary = "获取当前授权范围概览，未授权指标返回null")
    public CommonResult<Dashboard> dashboard(@Valid Query req) {
        return success(service.dashboard(req));
    }
}
