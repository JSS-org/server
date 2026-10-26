package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRefundVO.*;
import cn.iocoder.yudao.module.gym.service.sales.GymRefundApplicationService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym")
@Validated
@Tag(name = "小程序 - 退款申请")
public class AppGymRefundApplicationsController {
    @Resource private GymRefundApplicationService service;

    @PostMapping("/orders/{id}/refund-applications")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Submitted> create(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Create req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(id, req, key));
    }

    @GetMapping("/me/refund-applications/{id}")
    public CommonResult<Application> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }
}
