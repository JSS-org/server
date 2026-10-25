package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileFeedbackVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileFeedbackService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym")
@PreAuthorize("isAuthenticated()")
@Tag(name = "小程序 - 本人反馈")
public class AppGymFeedbackController {
    @Resource private GymMobileFeedbackService service;

    @PostMapping("/feedbacks")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Receipt> submit(
            @Valid @RequestBody Submit req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.submit(req, key));
    }

    @GetMapping("/me/feedbacks")
    public CommonResult<PageResult<OwnFeedback>> mine(@Valid OwnPage req) {
        return success(service.mine(req));
    }
}
