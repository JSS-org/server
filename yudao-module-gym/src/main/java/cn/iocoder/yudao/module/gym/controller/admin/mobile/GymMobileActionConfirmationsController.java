package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymActionConfirmationVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileActionConfirmationsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/action-confirmations")
@Tag(name = "工作台 - 设备操作二次确认")
public class GymMobileActionConfirmationsController {
    @Resource private GymMobileActionConfirmationsService service;

    @PostMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('gym:devices:command')")
    public CommonResult<Result> create(
            @Valid @RequestBody Request req, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return success(service.create(req));
    }

    // Never forward OTP/token bodies to generic exception logs, even on unexpected failures.
    @ExceptionHandler(Exception.class)
    public CommonResult<?> failure(Exception error, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        if (error instanceof ServiceException e) return CommonResult.error(e);
        if (error instanceof MethodArgumentNotValidException
                || error instanceof HttpMessageNotReadableException)
            return CommonResult.error(400, "二次确认请求格式不合法");
        if (error instanceof org.springframework.security.access.AccessDeniedException)
            return CommonResult.error(403, "无设备操作权限");
        return CommonResult.error(500, "二次确认暂时不可用");
    }
}
