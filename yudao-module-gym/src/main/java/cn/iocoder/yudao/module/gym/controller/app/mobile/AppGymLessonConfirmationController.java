package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.LessonRecord;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileLessonVO.Confirm;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileLessonService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/lesson-confirmations")
@PreAuthorize("isAuthenticated()")
public class AppGymLessonConfirmationController {
    @Resource private GymMobileLessonService service;

    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(
            requestEnable = false,
            responseEnable = false)
    @PostMapping
    @Operation(summary = "本人持有效二维码签课，要求真实签到并幂等扣课")
    public CommonResult<LessonRecord> confirm(
            @Valid @RequestBody Confirm req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.confirm(req, key));
    }
}
