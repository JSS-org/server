package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileMemberSummaryVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileMemberSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/reports")
@Validated
@PreAuthorize("@ss.hasPermission('gym:reports:read')")
@Tag(name = "移动工作台 - 会员汇总")
public class GymMobileMemberSummaryController {
    @Resource private GymMobileMemberSummaryService service;

    @GetMapping("/member-summary")
    @Operation(summary = "统计授权门店当前会员及权益人数，按日期筛选新增会员")
    public CommonResult<Summary> summary(@Valid Query req) {
        return success(service.summary(req));
    }
}
