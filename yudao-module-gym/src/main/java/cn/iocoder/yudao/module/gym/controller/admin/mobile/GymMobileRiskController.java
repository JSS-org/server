package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileRiskVO.*;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileRiskService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/risk-alerts")
@Validated
@Tag(name = "工作台 - 风控告警处置")
public class GymMobileRiskController {
    @Resource private GymMobileRiskService service;

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @GetMapping
    @PreAuthorize("@ss.hasPermission('gym:risk:read')")
    public CommonResult<PageResult<Alert>> page(@Valid Query req) {
        return success(service.page(req));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PostMapping("/{id}/resolutions")
    @PreAuthorize("@ss.hasPermission('gym:risk:read') && @ss.hasPermission('gym:risk:resolve')")
    public CommonResult<Alert> resolve(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Resolve req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.resolve(id, req, key));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @GetMapping("/{id}/evidence/{fileId}/content")
    @PreAuthorize("@ss.hasPermission('gym:risk:read')")
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
        Object route =
                req.getAttribute(
                        org.springframework.web.servlet.HandlerMapping
                                .BEST_MATCHING_PATTERN_ATTRIBUTE);
        var result = CommonResult.error(error);
        if (route == null || !route.toString().endsWith("/content"))
            return org.springframework.http.ResponseEntity.ok(result);
        int code = error.getCode();
        int status =
                code
                                        == cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants
                                                .FILE_NOT_EXISTS
                                                .getCode()
                                || code
                                        == cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants
                                                .FILE_CONTENT_NOT_EXISTS
                                                .getCode()
                                || code
                                        == cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants
                                                .RISK_ALERT_NOT_EXISTS
                                                .getCode()
                        ? 404
                        : code == 403
                                        || code
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
