package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileActivityVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileActivityService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym")
@Validated
@Tag(name = "小程序 - 活动与本人报名")
public class AppGymActivitiesController {
    @Resource private GymMobileActivityService service;

    @GetMapping("/activities")
    @PermitAll
    public CommonResult<PageResult<Activity>> page(@Valid ActivityPage req) {
        return success(service.page(req));
    }

    @GetMapping("/activities/{id}")
    @PermitAll
    public CommonResult<Activity> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }

    @PostMapping("/activities/{id}/registrations")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Registration> register(
            @PathVariable("id") @Positive Long id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody(required = false) String body) {
        if (body != null && !body.isBlank()) throw exception(MOBILE_REQUEST_INVALID);
        return success(service.register(id, key));
    }

    @DeleteMapping("/activities/{id}/registrations/me")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Cancelled> cancel(@PathVariable("id") @Positive Long id) {
        return success(service.cancel(id));
    }

    @GetMapping("/me/activity-registrations")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<History>> history(@Valid RegistrationPage req) {
        return success(service.history(req));
    }
}
