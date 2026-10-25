package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileOrdersVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileOrdersService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym")
@Validated
@Tag(name = "小程序 - 本人订单")
public class AppGymOrdersController {
    @Resource private GymMobileOrdersService service;

    @PostMapping("/orders")
    public CommonResult<Order> create(
            @Valid @RequestBody Create req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(req, key));
    }

    @GetMapping("/me/orders")
    public CommonResult<PageResult<Order>> page(@Valid Query req) {
        return success(service.page(req));
    }

    @GetMapping("/me/orders/{id}")
    public CommonResult<Order> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }

    @PostMapping("/orders/{id}/cancellations")
    public CommonResult<Order> cancel(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Cancel req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.cancel(id, req, key));
    }
}
