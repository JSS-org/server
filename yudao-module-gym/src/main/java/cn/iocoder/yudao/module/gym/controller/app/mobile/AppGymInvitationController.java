package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileInvitationVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileInvitationService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/me/invitations")
@Validated
@Tag(name = "会员 - 邀请推广")
public class AppGymInvitationController {
    @Resource private GymMobileInvitationService service;

    @PostMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Invitation> create(
            @Valid @RequestBody Create req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(req, key));
    }
}
