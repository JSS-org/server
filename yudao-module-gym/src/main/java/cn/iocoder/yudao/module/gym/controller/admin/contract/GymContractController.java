package cn.iocoder.yudao.module.gym.controller.admin.contract;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.contract.vo.*;
import cn.iocoder.yudao.module.gym.service.contract.GymContractService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 合同记录")
@RestController
@RequestMapping("/gym/contracts")
@Validated
public class GymContractController {
    @Resource private GymContractService contracts;

    @GetMapping
    @PreAuthorize("@ss.hasAnyPermissions('gym:contracts:read','gym:approvals:create')")
    public CommonResult<PageResult<GymContractRespVO>> page(@Valid GymBusinessPageReqVO req) {
        return success(contracts.page(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('gym:contracts:read')")
    public CommonResult<GymContractRespVO> get(@PathVariable("id") @Positive Long id) {
        return success(contracts.get(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasAnyPermissions('gym:contracts:manage','gym:contracts:templates')")
    public CommonResult<GymContractRespVO> create(@Valid @RequestBody GymContractCreateReqVO req) {
        return success(contracts.create(req));
    }

    @GetMapping("/{id}/reminders")
    @PreAuthorize("@ss.hasPermission('gym:contracts:read')")
    public CommonResult<PageResult<GymContractReminderRespVO>> reminders(
            @PathVariable("id") @Positive Long id, @Valid GymBusinessPageReqVO req) {
        return success(contracts.reminders(id, req));
    }

    @PostMapping("/{id}/reminders")
    @PreAuthorize("@ss.hasPermission('gym:contracts:remind')")
    public CommonResult<GymContractReminderRespVO> remind(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody GymContractReminderReqVO req,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return success(contracts.remind(id, req, key));
    }
}
