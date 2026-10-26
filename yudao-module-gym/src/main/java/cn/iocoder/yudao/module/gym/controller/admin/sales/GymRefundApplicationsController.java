package cn.iocoder.yudao.module.gym.controller.admin.sales;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRefundVO.*;
import cn.iocoder.yudao.module.gym.service.sales.GymRefundApplicationService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/refund-applications")
@Validated
@Tag(name = "管理后台 - 会员退款申请审核")
public class GymRefundApplicationsController {
    @Resource private GymRefundApplicationService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    public CommonResult<PageResult<Review>> page(@Valid Query req) {
        return success(service.page(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    public CommonResult<Review> get(@PathVariable("id") @Positive Long id) {
        return success(service.review(id));
    }

    @PostMapping("/{id}/decisions")
    @PreAuthorize(
            "@ss.hasPermission('gym:sales:read') && @ss.hasPermission('gym:approvals:decide')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Review> decide(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Decision req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.decide(id, req, key));
    }
}
