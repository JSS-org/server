package cn.iocoder.yudao.module.gym.controller.admin.member;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;
import cn.iocoder.yudao.module.gym.service.member.GymTrainingPlanService;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 会员训练档案")
@RestController
@RequestMapping("/gym/training-plans")
@Validated
public class GymTrainingPlanController {
    @Resource private GymTrainingPlanService plans;

    @GetMapping
    @Operation(summary = "分页查询授权会员训练计划")
    @PreAuthorize("@ss.hasPermission('gym:measurements:read')")
    public CommonResult<PageResult<GymTrainingPlanRespVO>> page(@Valid GymBusinessPageReqVO req) {
        return success(plans.page(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询授权会员训练计划详情")
    @PreAuthorize("@ss.hasPermission('gym:measurements:read')")
    public CommonResult<GymTrainingPlanRespVO> get(@PathVariable("id") @Positive Long id) {
        return success(plans.get(id));
    }

    @PostMapping
    @Operation(summary = "新增训练计划，内容和附件至少一项")
    @PreAuthorize("@ss.hasPermission('gym:measurements:write')")
    public CommonResult<GymTrainingPlanRespVO> create(@RequestBody GymTrainingPlanSaveReqVO req) {
        return success(plans.create(req));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "修改或归档训练计划；会员门店不可变")
    @PreAuthorize("@ss.hasPermission('gym:measurements:write')")
    public CommonResult<GymTrainingPlanRespVO> patch(
            @PathVariable("id") @Positive Long id, @RequestBody GymTrainingPlanSaveReqVO req) {
        return success(plans.patch(id, req));
    }
}
