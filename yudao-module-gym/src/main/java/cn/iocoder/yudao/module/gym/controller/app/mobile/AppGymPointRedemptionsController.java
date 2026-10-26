package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointRedemptionVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePointRedemptionsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym")
@Validated
@Tag(name = "会员 - 积分兑换")
public class AppGymPointRedemptionsController {
    @Resource private GymMobilePointRedemptionsService service;

    @PostMapping("/point-redemptions")
    public CommonResult<Result> create(
            @Valid @RequestBody Create req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(req, key));
    }

    @GetMapping("/me/point-redemptions/{id}")
    public CommonResult<Result> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }
}
