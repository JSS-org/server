package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkFileUploadReqVO;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkFilesService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/gym/mobile/files")
@Validated
@Tag(name = "工作台 - 业务文件")
public class GymMobileWorkFilesController {
    @Resource private GymMobileWorkFilesService service;
    @Resource private GlobalExceptionHandler exceptionHandler;

    @InitBinder
    public void bind(WebDataBinder binder) {
        binder.setAllowedFields("file", "purpose", "storeId", "memberId");
        // Multipart fields are the contract; HTTP headers must never become business fields.
        if (binder
                instanceof
                org.springframework.web.servlet.support.ExtendedServletRequestDataBinder extended)
            extended.setHeaderPredicate(name -> false);
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("@ss.hasPermission('gym:files:upload')")
    @Operation(summary = "上传指定业务用途和目标的文件")
    public CommonResult<GymMobileWorkFilesService.UploadedFile> upload(
            @Valid GymMobileWorkFileUploadReqVO req, BindingResult binding) throws BindException {
        if (binding.getSuppressedFields().length > 0) throw exception(MOBILE_REQUEST_INVALID);
        if (binding.hasErrors()) throw new BindException(binding);
        return success(service.upload(req));
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("@ss.hasPermission('gym:files:read')")
    @Operation(summary = "按当前业务及目标权限下载文件")
    public void content(@PathVariable("id") @Positive Long id, HttpServletResponse response)
            throws IOException {
        var file = service.content(id);
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        GymFileTypeUtils.writeAttachment(
                response, file.getName(), file.getMimeType(), file.getContent());
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<CommonResult<?>> failure(HttpServletRequest req, ServiceException e) {
        var result = exceptionHandler.allExceptionHandler(req, e);
        Object route =
                req.getAttribute(
                        org.springframework.web.servlet.HandlerMapping
                                .BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (route == null || !route.toString().endsWith("/{id}/content"))
            return ResponseEntity.ok(result);
        int code = result.getCode();
        int status =
                code == FILE_NOT_EXISTS.getCode() || code == FILE_CONTENT_NOT_EXISTS.getCode()
                        ? 404
                        : code == MOBILE_FILE_NOT_OWNED.getCode()
                                        || code == FILE_STORE_NO_PERMISSION.getCode()
                                        || code == STORE_NO_PERMISSION.getCode()
                                        || code == MOBILE_IDENTITY_INVALID.getCode()
                                        || code == MOBILE_EMPLOYEE_REQUIRED.getCode()
                                        || code == MOBILE_COACH_NOT_AVAILABLE.getCode()
                                ? 403
                                : code >= 400 && code <= 599 ? code : 400;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(result);
    }
}
