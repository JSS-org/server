package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDevicesVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileDevicesService;

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
@Tag(name = "工作台 - 设备及柜格查询")
public class GymMobileDevicesController {
    @Resource private GymMobileDevicesService service;

    @GetMapping("/devices")
    @PreAuthorize("@ss.hasPermission('gym:devices:read')")
    public CommonResult<PageResult<Device>> devices(@Valid Query req) {
        return success(service.devices(req));
    }

    @GetMapping("/devices/{id}/compartments")
    @PreAuthorize("@ss.hasPermission('gym:devices:read')")
    public CommonResult<PageResult<Compartment>> compartments(
            @PathVariable("id") @Positive Long id, @Valid PageParam req) {
        return success(service.compartments(id, req));
    }
}
