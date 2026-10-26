package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
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
@Tag(name = "会员 - 站内通知")
public class AppGymNotificationsController {
    @Resource private GymMobileNotificationsService service;

    @GetMapping("/gym/me/notifications")
    public CommonResult<PageResult<Notification>> page(@Valid Query req) {
        return success(service.memberPage(req));
    }

    @PostMapping("/gym/me/notifications/{id}/read")
    public CommonResult<Boolean> read(@PathVariable("id") @Positive Long id) {
        return success(service.memberRead(id));
    }

    @PostMapping("/gym/me/notification-preferences")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Saved> preferences(@Valid @RequestBody Preferences req) {
        return success(service.preferences(req));
    }
}
