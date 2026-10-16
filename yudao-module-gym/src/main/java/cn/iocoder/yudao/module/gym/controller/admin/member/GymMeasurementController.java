package cn.iocoder.yudao.module.gym.controller.admin.member;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym Measurement")
@RestController
@RequestMapping("/gym/body-measurements")
@Validated
public class GymMeasurementController {
    @Resource private GymMemberRecordsService records;

    @GetMapping
    @Operation(summary = "分页查询授权范围记录")
    @PreAuthorize("@ss.hasPermission('gym:measurements:read')")
    public CommonResult<PageResult<GymMeasurementRespVO>> page(@Valid GymBusinessPageReqVO req) {
        return success(records.measurements(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询授权记录详情")
    @PreAuthorize("@ss.hasPermission('gym:measurements:read')")
    public CommonResult<GymMeasurementRespVO> get(@PathVariable("id") @Positive Long id) {
        return success(records.measurement(id));
    }

    @PostMapping
    @Operation(summary = "新增业务记录")
    @PreAuthorize("@ss.hasPermission('gym:measurements:write')")
    public CommonResult<GymMeasurementRespVO> create(
            @RequestBody @Valid GymMeasurementSaveReqVO req) {
        return success(records.createMeasurement(req));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "部分更新业务记录")
    @PreAuthorize("@ss.hasPermission('gym:measurements:write')")
    public CommonResult<GymMeasurementRespVO> patch(
            @PathVariable("id") @Positive Long id, @RequestBody GymMeasurementSaveReqVO req) {
        return success(records.patchMeasurement(id, req));
    }
}
