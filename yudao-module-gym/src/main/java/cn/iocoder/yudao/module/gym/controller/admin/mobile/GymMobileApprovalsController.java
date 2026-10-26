package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileApprovalsVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileApprovalsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile")
@Validated
@Tag(name = "移动工作台 - 审批")
public class GymMobileApprovalsController {
    @Resource private GymMobileApprovalsService service;

    @GetMapping("/approvals")
    @PreAuthorize("@ss.hasPermission('gym:approvals:read')")
    public CommonResult<PageResult<Approval>> page(@Valid Query req) {
        return success(service.page(req));
    }

    @GetMapping("/approvals/{id}")
    @PreAuthorize("@ss.hasPermission('gym:approvals:read')")
    public CommonResult<Approval> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }

    @GetMapping("/approval-options")
    @PreAuthorize("@ss.hasPermission('gym:approvals:create')")
    public CommonResult<Options> options(@Valid OptionsQuery req) {
        return success(service.options(req));
    }

    @PostMapping("/approvals/{id}/decisions")
    @PreAuthorize(
            "@ss.hasPermission('gym:approvals:read') and @ss.hasPermission('gym:approvals:decide')")
    public CommonResult<Approval> decide(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Decision req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.decide(id, req, key));
    }
}
