package cn.iocoder.yudao.module.gym.controller.admin.salary;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.salary.vo.*;
import cn.iocoder.yudao.module.gym.service.salary.GymSalaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 薪资模板与关联")
@RestController
@RequestMapping("/gym")
@Validated
public class GymSalaryController {
    @Resource private GymSalaryService salaries;

    @GetMapping("/salary-templates")
    @Operation(summary = "分页查询薪资模板")
    @PreAuthorize("@ss.hasAnyPermissions('gym:payroll:templates', 'gym:payroll:read')")
    public CommonResult<PageResult<GymSalaryTemplateRespVO>> templates(
            @Valid GymSalaryPageReqVO req) {
        return success(salaries.templates(req));
    }

    @GetMapping("/salary-templates/{id}")
    @Operation(summary = "查询薪资模板详情")
    @PreAuthorize("@ss.hasAnyPermissions('gym:payroll:templates', 'gym:payroll:read')")
    public CommonResult<GymSalaryTemplateRespVO> template(@PathVariable("id") @Positive Long id) {
        return success(salaries.template(id));
    }

    @PostMapping("/salary-templates")
    @Operation(summary = "新建薪资模板草稿")
    @PreAuthorize("@ss.hasPermission('gym:payroll:templates')")
    public CommonResult<GymSalaryTemplateRespVO> create(
            @RequestBody @Valid GymSalaryTemplateSaveReqVO req) {
        return success(salaries.create(req));
    }

    @PatchMapping("/salary-templates/{id}")
    @Operation(summary = "修改草稿或发布；已发布模板不可变")
    @PreAuthorize("@ss.hasPermission('gym:payroll:templates')")
    public CommonResult<GymSalaryTemplateRespVO> patch(
            @PathVariable("id") @Positive Long id, @RequestBody GymSalaryTemplateSaveReqVO req) {
        return success(salaries.patch(id, req));
    }

    @PostMapping("/salary-templates/{id}/versions")
    @Operation(summary = "创建薪资模板新版本草稿")
    @PreAuthorize("@ss.hasPermission('gym:payroll:templates')")
    public CommonResult<GymSalaryTemplateRespVO> version(
            @PathVariable("id") @Positive Long id,
            @RequestBody @Valid GymSalaryTemplateSaveReqVO req) {
        return success(salaries.version(id, req));
    }

    @GetMapping("/employee-salary-assignments")
    @Operation(summary = "分页查询授权门店员工薪资历史")
    @PreAuthorize("@ss.hasPermission('gym:payroll:read')")
    public CommonResult<PageResult<GymSalaryAssignmentRespVO>> assignments(
            @Valid GymSalaryPageReqVO req) {
        return success(salaries.assignments(req));
    }

    @GetMapping("/employee-salary-assignments/{id}")
    @Operation(summary = "查询授权门店薪资关联详情")
    @PreAuthorize("@ss.hasPermission('gym:payroll:read')")
    public CommonResult<GymSalaryAssignmentRespVO> assignment(
            @PathVariable("id") @Positive Long id) {
        return success(salaries.assignment(id));
    }

    @PostMapping("/employee-salary-assignments")
    @Operation(summary = "创建员工薪资关联；新配置自动结束更早的长期配置")
    @PreAuthorize("@ss.hasPermission('gym:payroll:templates')")
    public CommonResult<GymSalaryAssignmentRespVO> createAssignment(
            @RequestBody @Valid GymSalaryAssignmentReqVO req) {
        return success(salaries.createAssignment(req));
    }

    @PatchMapping("/employee-salary-assignments/{id}")
    @Operation(summary = "调整薪资关联结束日期，其他字段不可变")
    @PreAuthorize("@ss.hasPermission('gym:payroll:templates')")
    public CommonResult<GymSalaryAssignmentRespVO> patchAssignment(
            @PathVariable("id") @Positive Long id, @RequestBody GymSalaryAssignmentReqVO req) {
        return success(salaries.patchAssignment(id, req));
    }
}
