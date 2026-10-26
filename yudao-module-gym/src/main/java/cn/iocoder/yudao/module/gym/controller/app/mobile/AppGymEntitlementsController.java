package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEntitlementsVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileEntitlementsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小程序 - 本人会员卡与课包")
@RestController
@RequestMapping("/gym/me")
@Validated
public class AppGymEntitlementsController {
    @Resource private GymMobileEntitlementsService entitlements;

    @GetMapping("/cards")
    @Operation(summary = "本人会员卡分页，按实际可用门店和生效状态筛选")
    public CommonResult<PageResult<MemberCard>> cards(@Valid CardPage req) {
        return success(entitlements.cards(req));
    }

    @GetMapping("/cards/{id}")
    @Operation(summary = "本人会员卡详情，包含失效历史")
    public CommonResult<MemberCard> card(@PathVariable("id") @Positive Long id) {
        return success(entitlements.card(id));
    }

    @GetMapping("/course-packs")
    @Operation(summary = "本人课包分页与课时余额")
    public CommonResult<PageResult<CoursePack>> packs(@Valid PackPage req) {
        return success(entitlements.packs(req));
    }
}
