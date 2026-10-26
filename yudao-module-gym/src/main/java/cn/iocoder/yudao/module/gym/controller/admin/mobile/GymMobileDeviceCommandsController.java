package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDeviceCommandVO;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileDeviceCommandsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/device-commands")
@Validated
@Tag(name = "工作台 - 设备命令结果")
public class GymMobileDeviceCommandsController {
    @Resource private GymMobileDeviceCommandsService service;

    // Owner OR current manager permission/scope is checked inside the service.
    @GetMapping("/{id}")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<GymMobileDeviceCommandVO> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }
}
