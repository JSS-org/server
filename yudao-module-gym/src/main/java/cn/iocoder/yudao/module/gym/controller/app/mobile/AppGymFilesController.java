package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileFileUploadReqVO;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileFilesService;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileFilesService.UploadedFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "小程序 - 会员受控文件")
@RestController
@RequestMapping("/gym/files")
@Validated
public class AppGymFilesController {
    @Resource private GymMobileFilesService files;
    @Resource private GlobalExceptionHandler exceptionHandler;

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "上传本人头像、人脸和反馈图片")
    public CommonResult<UploadedFile> upload(@Valid GymMobileFileUploadReqVO req) {
        return success(files.upload(req));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "下载本人文件或绑定本人的档案，禁止下载人脸原件")
    public void content(@PathVariable("id") @Positive Long id, HttpServletResponse response)
            throws IOException {
        var file = files.content(id);
        response.setHeader("Cache-Control", "private, no-store");
        GymFileTypeUtils.writeAttachment(
                response, file.getName(), file.getMimeType(), file.getContent());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult<?>> failure(HttpServletRequest req, Exception e) {
        CommonResult<?> result = exceptionHandler.allExceptionHandler(req, e);
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
                                ? 403
                                : code >= 400 && code <= 599 ? code : 400;
        return ResponseEntity.status(status).body(result);
    }
}
