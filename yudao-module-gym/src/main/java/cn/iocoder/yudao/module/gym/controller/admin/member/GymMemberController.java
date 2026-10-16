package cn.iocoder.yudao.module.gym.controller.admin.member;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 会员")
@RestController
@RequestMapping("/gym/members")
@Validated
public class GymMemberController {
    @Resource private GymMemberService members;
    @Resource private cn.iocoder.yudao.module.gym.service.card.GymCardService cards;
    @Resource private cn.iocoder.yudao.module.gym.service.course.GymCourseService courses;

    @GetMapping
    @Operation(summary = "分页查询授权范围会员")
    @PreAuthorize("@ss.hasPermission('gym:members:read')")
    public CommonResult<PageResult<GymMemberRespVO>> page(@Valid GymBusinessPageReqVO req) {
        return success(members.page(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询授权会员详情")
    @PreAuthorize("@ss.hasPermission('gym:members:read')")
    public CommonResult<GymMemberRespVO> get(@PathVariable("id") @Positive Long id) {
        var detail = members.get(id);
        var req = new GymBusinessPageReqVO();
        req.setPageSize(100);
        detail.setCards(cards.allCards(id, req));
        detail.setCoursePacks(courses.allPacks(id, req));
        return success(detail);
    }

    @PostMapping
    @Operation(summary = "在授权门店建立会员档案")
    @PreAuthorize("@ss.hasPermission('gym:members:write')")
    public CommonResult<GymMemberRespVO> create(@RequestBody GymMemberSaveReqVO req) {
        return success(members.create(req));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "有权管理员修改会员资料；建档门店不可变")
    @PreAuthorize("@ss.hasPermission('gym:members:write')")
    public CommonResult<GymMemberRespVO> patch(
            @PathVariable("id") @Positive Long id, @RequestBody GymMemberSaveReqVO req) {
        return success(members.patch(id, req));
    }

    @Resource private cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService coaches;

    @GetMapping("/{id}/stores/{storeId}/coaches")
    @PreAuthorize("@ss.hasPermission('gym:members:write')")
    public CommonResult<java.util.List<GymMemberCoachRespVO>> coaches(
            @PathVariable("id") @Positive Long id,
            @PathVariable("storeId") @Positive Long storeId) {
        return success(coaches.list(id, storeId));
    }

    @PutMapping("/{id}/stores/{storeId}/coaches")
    @PreAuthorize("@ss.hasPermission('gym:members:write')")
    public CommonResult<java.util.List<GymMemberCoachRespVO>> replaceCoaches(
            @PathVariable("id") @Positive Long id,
            @PathVariable("storeId") @Positive Long storeId,
            @Valid @RequestBody GymMemberCoachesReqVO req) {
        return success(coaches.replace(id, storeId, req));
    }
}
