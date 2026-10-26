package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileActivityVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileActivityService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/activities")
@Tag(name = "移动工作台 - 活动创建")
public class GymMobileActivityController {
    @Resource private GymMobileActivityService service;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('gym:activities:manage')")
    public CommonResult<Activity> create(
            @Valid @RequestBody Create req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(req, key));
    }
}
