package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileFeedbackVO.*;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileFeedbackService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/feedbacks")
@Validated
@Tag(name = "移动工作台 - 匿名反馈与回复")
public class GymMobileFeedbackController {
    @Resource private GymMobileFeedbackService service;

    @Resource
    private cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler exceptionHandler;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('gym:feedbacks:read')")
    public CommonResult<PageResult<StoreFeedback>> store(@Valid StorePage req) {
        return success(service.store(req));
    }

    @PostMapping("/{id}/replies")
    @PreAuthorize("@ss.hasPermission('gym:feedbacks:reply')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Replied> reply(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Reply req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.reply(id, req, key));
    }

    @GetMapping("/{id}/attachments/{fileId}/content")
    @PreAuthorize("@ss.hasPermission('gym:feedbacks:read')")
    public void attachment(
            @PathVariable("id") @Positive Long id,
            @PathVariable("fileId") @Positive Long fileId,
            HttpServletResponse response)
            throws java.io.IOException {
        var file = service.attachment(id, fileId);
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        GymFileTypeUtils.writeAttachment(
                response, file.getName(), file.getMimeType(), file.getContent());
    }

    @ExceptionHandler(cn.iocoder.yudao.framework.common.exception.ServiceException.class)
    public org.springframework.http.ResponseEntity<CommonResult<?>> failure(
            jakarta.servlet.http.HttpServletRequest req,
            cn.iocoder.yudao.framework.common.exception.ServiceException error) {
        var result = exceptionHandler.allExceptionHandler(req, error);
        Object route =
                req.getAttribute(
                        org.springframework.web.servlet.HandlerMapping
                                .BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (route == null || !route.toString().endsWith("/content"))
            return org.springframework.http.ResponseEntity.ok(result);
        int code = result.getCode();
        int status =
                code
                                        == cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants
                                                .FILE_NOT_EXISTS
                                                .getCode()
                                || code
                                        == cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants
                                                .FEEDBACK_NOT_EXISTS
                                                .getCode()
                        ? 404
                        : code
                                                == cn.iocoder.yudao.module.gym.enums
                                                        .ErrorCodeConstants.STORE_NO_PERMISSION
                                                        .getCode()
                                        || code
                                                == cn.iocoder.yudao.module.gym.enums
                                                        .ErrorCodeConstants.MOBILE_IDENTITY_INVALID
                                                        .getCode()
                                ? 403
                                : 400;
        return org.springframework.http.ResponseEntity.status(status)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(result);
    }
}
