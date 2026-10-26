package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileIdentityVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileIdentityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小程序 - 会员身份与资料")
@RestController
@RequestMapping("/gym/me")
@Validated
public class AppGymIdentityController {
    @Resource private GymMobileIdentityService identities;

    @PostMapping("/profile-initializations")
    @Operation(summary = "关联已验证手机号并幂等创建会员档案")
    public CommonResult<Initialization> initialize(
            @Valid @RequestBody Initialize req, @RequestHeader("Idempotency-Key") String key) {
        return success(identities.initialize(req, key));
    }

    @GetMapping("/context")
    @Operation(summary = "获取本人身份及档案完成状态")
    public CommonResult<Context> context() {
        return success(identities.context());
    }

    @GetMapping("/profile")
    @Operation(summary = "获取本人脱敏资料")
    public CommonResult<Profile> profile() {
        return success(identities.profile());
    }

    @PatchMapping("/profile")
    @Operation(summary = "修改本人昵称和头像，拒绝手机号等额外字段")
    public CommonResult<Profile> patch(@Valid @RequestBody ProfilePatch req) {
        return success(identities.updateProfile(req));
    }
}
