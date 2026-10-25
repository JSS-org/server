package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileNotificationsVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileNotificationsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("")
@Validated
@Tag(name = "工作台 - 站内通知")
public class GymMobileNotificationsController {
    @Resource private GymMobileNotificationsService service;

    @GetMapping("/gym/mobile/notifications")
    public CommonResult<PageResult<Notification>> page(@Valid Query req) {
        return success(service.staffPage(req));
    }

    @PostMapping("/gym/mobile/notifications/{id}/read")
    public CommonResult<Boolean> read(@PathVariable("id") @Positive Long id) {
        return success(service.staffRead(id));
    }
}
