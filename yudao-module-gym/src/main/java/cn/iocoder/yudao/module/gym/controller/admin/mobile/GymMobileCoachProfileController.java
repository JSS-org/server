package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileCoachProfilePatch;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCoachVO.Coach;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCoachProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/me/coach-profile")
@Validated
@PreAuthorize("isAuthenticated()")
@Tag(name = "工作台 - 本人教练资料")
public class GymMobileCoachProfileController {
    @Resource private GymMobileCoachProfileService service;
    @Resource private GlobalExceptionHandler exceptionHandler;

    @GetMapping
    @Operation(summary = "读取本人教练资料，含未发布草稿")
    public CommonResult<Coach> get() {
        return success(service.get());
    }

    @PatchMapping
    @Operation(summary = "仅修改本人介绍和图片，不更改发布状态")
    public CommonResult<Coach> patch(@Valid @RequestBody GymMobileCoachProfilePatch req) {
        return success(service.patch(req));
    }

    @GetMapping("/files/{id}/content")
    @Operation(summary = "读取当前绑定本人教练资料的图片，需后台令牌")
    public ResponseEntity<ByteArrayResource> content(@PathVariable("id") @Positive Long id) {
        var file = service.content(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(file.getContent()));
    }

    @ExceptionHandler(cn.iocoder.yudao.framework.common.exception.ServiceException.class)
    public ResponseEntity<CommonResult<?>> failure(
            HttpServletRequest req,
            cn.iocoder.yudao.framework.common.exception.ServiceException e) {
        var result = exceptionHandler.allExceptionHandler(req, e);
        Object route =
                req.getAttribute(
                        org.springframework.web.servlet.HandlerMapping
                                .BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (route == null || !route.toString().endsWith("/files/{id}/content"))
            return ResponseEntity.ok(result);
        int code = result.getCode();
        int status =
                code == FILE_NOT_EXISTS.getCode() || code == FILE_CONTENT_NOT_EXISTS.getCode()
                        ? 404
                        : code == MOBILE_FILE_NOT_OWNED.getCode()
                                        || code == MOBILE_IDENTITY_INVALID.getCode()
                                        || code == MOBILE_EMPLOYEE_REQUIRED.getCode()
                                        || code == MOBILE_COACH_NOT_AVAILABLE.getCode()
                                ? 403
                                : code >= 400 && code <= 599 ? code : 400;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(result);
    }
}
