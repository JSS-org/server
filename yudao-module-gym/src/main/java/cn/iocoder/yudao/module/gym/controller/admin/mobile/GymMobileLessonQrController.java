package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileLessonVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileLessonService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/lesson-qr-codes")
public class GymMobileLessonQrController {
    @Resource private GymMobileLessonService service;

    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(
            requestEnable = false,
            responseEnable = false)
    @PostMapping
    @PreAuthorize("@ss.hasPermission('gym:bookings:write')")
    @Operation(summary = "为当前预约生成短时签课二维码凭据")
    public CommonResult<Qr> create(
            @Valid @RequestBody QrCreate req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.issue(req, key));
    }
}
