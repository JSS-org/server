package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileConversationsVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileConversationsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/me/conversations")
@Validated
@Tag(name = "会员 - 站内私信")
public class AppGymConversationsController {
    @Resource private GymMobileConversationsService service;

    @GetMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<PageResult<Conversation>> page(@Valid PageParam req) {
        return success(service.memberConversations(req));
    }

    @PostMapping("/{id}/read")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ReadState> read(
            @PathVariable("id") @Positive Long id, @Valid @RequestBody Read req) {
        return success(service.memberRead(id, req));
    }

    @GetMapping("/{id}/messages")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<PageResult<Message>> messages(
            @PathVariable("id") @Positive Long id, @Valid Query req) {
        return success(service.memberMessages(id, req));
    }

    @PostMapping("/{id}/messages")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Message> send(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Send req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.memberSend(id, req, key));
    }
}
