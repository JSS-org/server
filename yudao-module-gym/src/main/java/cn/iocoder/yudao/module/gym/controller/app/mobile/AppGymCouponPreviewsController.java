package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.UserRateLimiterKeyResolver;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponPreviewVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCouponPreviewsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/coupon-verification-previews")
@Tag(name = "会员 - 团购券预览")
public class AppGymCouponPreviewsController {
    @Resource private GymMobileCouponPreviewsService service;

    @PostMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @RateLimiter(time = 60, count = 5, keyResolver = UserRateLimiterKeyResolver.class)
    public CommonResult<Preview> preview(@Valid @RequestBody Request req) {
        return success(service.preview(req));
    }
}
