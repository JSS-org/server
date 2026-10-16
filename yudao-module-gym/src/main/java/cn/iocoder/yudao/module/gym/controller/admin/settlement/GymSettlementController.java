package cn.iocoder.yudao.module.gym.controller.admin.settlement;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.settlement.vo.*;
import cn.iocoder.yudao.module.gym.service.settlement.GymSettlementService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 收款与分账")
@RestController
@RequestMapping("/gym")
@Validated
public class GymSettlementController {
    @Resource private GymSettlementService settlements;

    @GetMapping("/payment-accounts")
    @PreAuthorize("@ss.hasPermission('gym:settlement:manage')")
    public CommonResult<PageResult<GymPaymentAccountRespVO>> accounts(
            @Valid GymBusinessPageReqVO req) {
        return success(settlements.accounts(req));
    }

    @GetMapping("/payment-accounts/{id}")
    @PreAuthorize("@ss.hasPermission('gym:settlement:manage')")
    public CommonResult<GymPaymentAccountRespVO> account(@PathVariable("id") @Positive Long id) {
        return success(settlements.account(id));
    }

    @PostMapping("/payment-accounts")
    @PreAuthorize("@ss.hasPermission('gym:settlement:manage')")
    public CommonResult<GymPaymentAccountRespVO> createAccount(
            @Valid @RequestBody GymPaymentAccountCreateReqVO req) {
        return success(settlements.createAccount(req));
    }

    @PatchMapping("/payment-accounts/{id}")
    @PreAuthorize("@ss.hasPermission('gym:settlement:manage')")
    public CommonResult<GymPaymentAccountRespVO> patchAccount(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody GymPaymentAccountPatchReqVO req) {
        return success(settlements.patchAccount(id, req));
    }

    @PostMapping("/payment-accounts/{id}/bindings")
    @PreAuthorize("@ss.hasPermission('gym:settlement:manage')")
    public CommonResult<GymPaymentAccountRespVO> bind(@PathVariable("id") @Positive Long id) {
        return success(settlements.bind(id));
    }

    @GetMapping("/stores/{storeId}/settlement-rules")
    @PreAuthorize("@ss.hasPermission('gym:settlement:manage')")
    public CommonResult<PageResult<GymSettlementRuleRespVO>> rules(
            @PathVariable("storeId") @Positive Long store, @Valid GymBusinessPageReqVO req) {
        return success(settlements.rules(store, req));
    }

    @PostMapping("/stores/{storeId}/settlement-rules")
    @PreAuthorize("@ss.hasPermission('gym:settlement:manage')")
    public CommonResult<GymSettlementRuleRespVO> createRule(
            @PathVariable("storeId") @Positive Long store,
            @Valid @RequestBody GymSettlementRuleSaveReqVO req) {
        return success(settlements.createRule(store, req));
    }

    @GetMapping("/settlement-rules/{id}")
    @PreAuthorize("@ss.hasPermission('gym:settlement:manage')")
    public CommonResult<GymSettlementRuleRespVO> rule(@PathVariable("id") @Positive Long id) {
        return success(settlements.rule(id));
    }

    @PatchMapping("/settlement-rules/{id}")
    @PreAuthorize("@ss.hasPermission('gym:settlement:manage')")
    public CommonResult<GymSettlementRuleRespVO> patchRule(
            @PathVariable("id") @Positive Long id, @RequestBody GymSettlementRuleSaveReqVO req) {
        return success(settlements.patchRule(id, req));
    }

    @GetMapping("/settlements")
    @PreAuthorize("@ss.hasPermission('gym:settlement:read')")
    public CommonResult<PageResult<GymSettlementRespVO>> records(@Valid GymBusinessPageReqVO req) {
        return success(settlements.records(req));
    }

    @GetMapping("/settlements/{id}")
    @PreAuthorize("@ss.hasPermission('gym:settlement:read')")
    public CommonResult<GymSettlementRespVO> record(@PathVariable("id") @Positive Long id) {
        return success(settlements.record(id));
    }
}
