package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponVerificationVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCouponVerificationsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym")
@Validated
@Tag(name = "会员 - 团购核销")
public class AppGymCouponVerificationsController {
    @Resource private GymMobileCouponVerificationsService service;

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PostMapping("/coupon-verifications")
    public CommonResult<Result> create(
            @Valid @RequestBody Create req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(req, key));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @GetMapping("/me/coupon-verifications/{id}")
    public CommonResult<Result> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }
}
