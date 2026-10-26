package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobilePayrollVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePayrollService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile")
@Validated
@Tag(name = "移动工作台 - 工资查询")
public class GymMobilePayrollController {
    @Resource private GymMobilePayrollService service;

    @GetMapping("/me/payrolls")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "本人已发布工资列表")
    public CommonResult<PageResult<Payroll>> mine(@Valid OwnPage req) {
        return success(service.mine(req));
    }

    @GetMapping("/me/payrolls/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "本人已发布工资详情")
    public CommonResult<Payroll> mine(@PathVariable("id") @Positive Long id) {
        return success(service.mine(id));
    }

    @GetMapping("/payrolls")
    @PreAuthorize("@ss.hasPermission('gym:payroll:read')")
    @Operation(summary = "授权门店员工工资列表")
    public CommonResult<PageResult<Payroll>> store(@Valid StorePage req) {
        return success(service.store(req));
    }

    @GetMapping("/payrolls/{id}")
    @PreAuthorize("@ss.hasPermission('gym:payroll:read')")
    @Operation(summary = "授权门店员工工资详情")
    public CommonResult<Payroll> store(@PathVariable("id") @Positive Long id) {
        return success(service.store(id));
    }
}
