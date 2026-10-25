package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileConversationsVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileConversationsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/member-conversations")
@Validated
@Tag(name = "工作台 - 站内私信")
public class GymMobileConversationsController {
    @Resource private GymMobileConversationsService service;

    @PostMapping
    @PreAuthorize(
            "@ss.hasPermission('gym:messages:read') && @ss.hasPermission('gym:messages:send')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Created> create(
            @Valid @RequestBody Create req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(req, key));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("@ss.hasPermission('gym:messages:read')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<PageResult<Message>> messages(
            @PathVariable("id") @Positive Long id, @Valid Query req) {
        return success(service.staffMessages(id, req));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize(
            "@ss.hasPermission('gym:messages:read') && @ss.hasPermission('gym:messages:send')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Message> send(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Send req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.staffSend(id, req, key));
    }
}
