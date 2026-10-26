package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePublicService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/public-media")
@Validated
@PermitAll
@Tag(name = "小程序 - 公开发布素材读取")
public class AppGymPublicMediaController {
    @Resource private GymMobilePublicService content;
    @Resource private GlobalExceptionHandler exceptionHandler;

    @GetMapping("/{id}/content")
    @Operation(summary = "重新校验公开引用后读取素材；支持字节范围请求")
    public ResponseEntity<ByteArrayResource> content(@PathVariable("id") @Positive Long id) {
        var file = content.mediaContent(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(file.getContent()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult<?>> failure(HttpServletRequest req, Exception e) {
        var result = exceptionHandler.allExceptionHandler(req, e);
        int code = result.getCode();
        return ResponseEntity.status(
                        code == FILE_NOT_EXISTS.getCode()
                                        || code == FILE_CONTENT_NOT_EXISTS.getCode()
                                ? 404
                                : code >= 400 && code <= 599 ? code : 400)
                .cacheControl(CacheControl.noStore())
                .body(result);
    }
}
