package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkRecordsVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordsVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkRecordsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "工作台 - 会员运动档案与合同")
@RestController
@RequestMapping("/gym/mobile")
@Validated
public class GymMobileWorkRecordsController {
    @Resource private GymMobileWorkRecordsService records;

    @GetMapping("/body-measurements")
    @PreAuthorize("@ss.hasPermission('gym:measurements:read')")
    @Operation(summary = "授权会员体测分页")
    public CommonResult<PageResult<Measurement>> measurements(@Valid Page req) {
        return success(records.measurements(req));
    }

    @PostMapping("/body-measurements")
    @PreAuthorize("@ss.hasPermission('gym:measurements:write')")
    @Operation(summary = "幂等录入授权会员体测")
    public CommonResult<Measurement> measurement(
            @Valid @RequestBody MeasurementCreate req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(records.createMeasurement(req, key));
    }

    @GetMapping("/training-plans")
    @PreAuthorize("@ss.hasPermission('gym:measurements:read')")
    @Operation(summary = "授权会员训练计划分页")
    public CommonResult<PageResult<TrainingPlan>> plans(@Valid Page req) {
        return success(records.plans(req));
    }

    @PostMapping("/training-plans")
    @PreAuthorize("@ss.hasPermission('gym:measurements:write')")
    @Operation(summary = "幂等创建授权会员训练计划")
    public CommonResult<TrainingPlan> plan(
            @Valid @RequestBody PlanCreate req, @RequestHeader("Idempotency-Key") String key) {
        return success(records.createPlan(req, key));
    }

    @GetMapping("/contracts")
    @PreAuthorize("@ss.hasPermission('gym:contracts:read')")
    @Operation(summary = "授权会员合同分页，不包含内部签署证据")
    public CommonResult<PageResult<Contract>> contracts(@Valid Page req) {
        return success(records.contracts(req));
    }

    @PostMapping("/contracts")
    @PreAuthorize("@ss.hasPermission('gym:contracts:manage')")
    @Operation(summary = "基于真实会员权益幂等发放合同原件")
    public CommonResult<Contract> contract(
            @Valid @RequestBody ContractCreate req, @RequestHeader("Idempotency-Key") String key) {
        return success(records.createContract(req, key));
    }
}
