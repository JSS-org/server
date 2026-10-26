package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkMembersVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkMemberQueryService;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkMembersService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "工作台 - 会员资料、跟进与分配")
@RestController
@RequestMapping("/gym/mobile/members")
@Validated
public class GymMobileWorkMembersController {
    @Resource private GymMobileWorkMembersService members;
    @Resource private GymMobileWorkMemberQueryService queries;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('gym:members:read')")
    @Operation(summary = "分页查询授权会员及当前门店权益摘要")
    public CommonResult<PageResult<WorkMember>> page(@Valid MemberPage req) {
        return success(queries.page(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('gym:members:read')")
    @Operation(summary = "查询授权会员在指定门店的资料和分配版本")
    public CommonResult<WorkMember> get(
            @PathVariable("id") @Positive Long id,
            @RequestParam("storeId") @Positive Long storeId) {
        return success(queries.get(id, storeId));
    }

    @GetMapping("/{id}/follow-ups")
    @PreAuthorize("@ss.hasPermission('gym:members:read')")
    @Operation(summary = "查询授权会员本门店跟进记录")
    public CommonResult<PageResult<Followup>> followups(
            @PathVariable("id") @Positive Long id, @Valid FollowupPage req) {
        return success(members.followups(id, req));
    }

    @PostMapping("/{id}/follow-ups")
    @PreAuthorize("@ss.hasPermission('gym:members:write')")
    @Operation(summary = "幂等添加跟进，员工身份取自当前账号")
    public CommonResult<Followup> followup(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody FollowupCreate req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(members.createFollowup(id, req, key));
    }

    @PutMapping("/{id}/assignments")
    @PreAuthorize("@ss.hasPermission('gym:members:write')")
    @Operation(summary = "以期望版本替换本门店负责关系，仅整店权限可操作")
    public CommonResult<AssignmentResult> assign(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Assignment req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(members.assign(id, req, key));
    }
}
