package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileSubscriptionsVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileSubscriptionsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/me/subscriptions")
@Validated
@Tag(name = "会员 - 本人连续包月协议")
public class AppGymSubscriptionsController {
    @Resource private GymMobileSubscriptionsService service;

    @GetMapping
    public CommonResult<PageResult<Subscription>> page(@Valid Query req) {
        return success(service.page(req));
    }

    @GetMapping("/{id}")
    public CommonResult<Subscription> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }
}
