package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordPageReqVO;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordsVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileRecordsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小程序 - 本人运动档案与合同")
@RestController
@RequestMapping("/gym/me")
@Validated
public class AppGymRecordsController {
    @Resource private GymMobileRecordsService records;

    @GetMapping("/body-measurements")
    @Operation(summary = "本人历史体测分页")
    public CommonResult<PageResult<Measurement>> measurements(@Valid GymMobileRecordPageReqVO req) {
        return success(records.measurements(req));
    }

    @GetMapping("/body-measurements/{id}")
    @Operation(summary = "本人体测详情")
    public CommonResult<Measurement> measurement(@PathVariable("id") @Positive Long id) {
        return success(records.measurement(id));
    }

    @GetMapping("/training-plans")
    @Operation(summary = "本人训练计划分页，含归档历史")
    public CommonResult<PageResult<TrainingPlan>> plans(@Valid GymMobileRecordPageReqVO req) {
        return success(records.plans(req));
    }

    @GetMapping("/training-plans/{id}")
    @Operation(summary = "本人训练计划详情")
    public CommonResult<TrainingPlan> plan(@PathVariable("id") @Positive Long id) {
        return success(records.plan(id));
    }

    @GetMapping("/contracts")
    @Operation(summary = "本人合同分页")
    public CommonResult<PageResult<Contract>> contracts(@Valid GymMobileRecordPageReqVO req) {
        return success(records.contracts(req));
    }

    @GetMapping("/contracts/{id}")
    @Operation(summary = "本人合同详情，不公开内部审批和签署设备证据")
    public CommonResult<Contract> contract(@PathVariable("id") @Positive Long id) {
        return success(records.contract(id));
    }

    @GetMapping("/checkins")
    @Operation(summary = "本人成功入场记录分页")
    public CommonResult<PageResult<Checkin>> checkins(@Valid GymMobileRecordPageReqVO req) {
        return success(records.checkins(req));
    }

    @GetMapping("/fitness-summary")
    @Operation(summary = "本人运动档案摘要")
    public CommonResult<FitnessSummary> summary(
            @RequestParam(value = "storeId", required = false) @Positive Long storeId) {
        return success(records.summary(storeId));
    }
}
