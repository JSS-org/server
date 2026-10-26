package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobilePerformanceVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePerformanceService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/reports")
@Validated
@PreAuthorize("@ss.hasPermission('gym:reports:read')")
@Tag(name = "移动工作台 - 员工与门店业绩")
public class GymMobilePerformanceController {
    @Resource private GymMobilePerformanceService service;

    @GetMapping("/performance")
    public CommonResult<Result> performance(@Valid Query req) {
        return success(service.performance(req));
    }
}
