package cn.iocoder.yudao.module.gym.controller.admin.approval;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.approval.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.service.approval.GymApprovalTemplateService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - gym 审批模板")
@RestController
@RequestMapping("/gym")
@Validated
public class GymApprovalTemplateController {
    @Resource private GymApprovalTemplateService templates;

    @GetMapping("/approval-templates")
    @PreAuthorize("@ss.hasPermission('gym:approvals:templates')")
    public CommonResult<PageResult<GymApprovalTemplateRespVO>> page(
            @Valid GymBusinessPageReqVO req) {
        return success(templates.page(req));
    }

    @GetMapping("/approval-templates/{id}")
    @PreAuthorize("@ss.hasPermission('gym:approvals:templates')")
    public CommonResult<GymApprovalTemplateRespVO> get(@PathVariable("id") @Positive Long id) {
        return success(templates.get(id));
    }

    @PostMapping("/approval-templates")
    @PreAuthorize("@ss.hasPermission('gym:approvals:templates')")
    public CommonResult<GymApprovalTemplateRespVO> create(
            @RequestBody GymApprovalTemplateSaveReqVO req) {
        return success(templates.create(req));
    }

    @PatchMapping("/approval-templates/{id}")
    @PreAuthorize("@ss.hasPermission('gym:approvals:templates')")
    public CommonResult<GymApprovalTemplateRespVO> patch(
            @PathVariable("id") @Positive Long id, @RequestBody GymApprovalTemplateSaveReqVO req) {
        return success(templates.patch(id, req));
    }

    @PostMapping("/approval-templates/{id}/versions")
    @PreAuthorize("@ss.hasPermission('gym:approvals:templates')")
    public CommonResult<GymApprovalTemplateRespVO> version(
            @PathVariable("id") @Positive Long id, @RequestBody GymApprovalTemplateSaveReqVO req) {
        return success(templates.version(id, req));
    }

    @GetMapping("/approval-options")
    @PreAuthorize("@ss.hasPermission('gym:approvals:create')")
    public CommonResult<Map<String, List<GymApprovalTemplateRespVO>>> options() {
        return success(Map.of("templates", templates.options()));
    }
}
