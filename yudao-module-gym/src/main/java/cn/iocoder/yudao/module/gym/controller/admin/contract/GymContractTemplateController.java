package cn.iocoder.yudao.module.gym.controller.admin.contract;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.contract.vo.*;
import cn.iocoder.yudao.module.gym.service.contract.GymContractTemplateService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 合同模板")
@RestController
@RequestMapping("/gym/contract-templates")
@Validated
public class GymContractTemplateController {
    @Resource private GymContractTemplateService templates;

    @GetMapping
    @PreAuthorize("@ss.hasAnyPermissions('gym:contracts:templates','gym:contracts:read')")
    public CommonResult<PageResult<GymContractTemplateRespVO>> page(
            @Valid GymBusinessPageReqVO req) {
        return success(templates.page(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAnyPermissions('gym:contracts:templates','gym:contracts:read')")
    public CommonResult<GymContractTemplateRespVO> get(@PathVariable("id") @Positive Long id) {
        return success(templates.get(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('gym:contracts:templates')")
    public CommonResult<GymContractTemplateRespVO> create(
            @RequestBody GymContractTemplateSaveReqVO req) {
        return success(templates.create(req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('gym:contracts:templates')")
    public CommonResult<GymContractTemplateRespVO> patch(
            @PathVariable("id") @Positive Long id, @RequestBody GymContractTemplateSaveReqVO req) {
        return success(templates.patch(id, req));
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("@ss.hasPermission('gym:contracts:templates')")
    public CommonResult<GymContractTemplateRespVO> version(
            @PathVariable("id") @Positive Long id, @RequestBody GymContractTemplateSaveReqVO req) {
        return success(templates.version(id, req));
    }
}
