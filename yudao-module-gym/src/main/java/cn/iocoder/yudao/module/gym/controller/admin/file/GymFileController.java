package cn.iocoder.yudao.module.gym.controller.admin.file;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.file.vo.GymFileRespVO;
import cn.iocoder.yudao.module.gym.controller.admin.file.vo.GymFileUploadReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.file.GymFileService;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * 管理后台 - gym 私有文件
 *
 * 对应旧 Mock（target/plan/10-backend-mock-api-inventory.md §4.1 后 3 行）：
 * | 旧 Mock                              | 本实现                                        | 权限码              |
 * | ------------------------------------ | --------------------------------------------- | ------------------- |
 * | POST /api/admin/v1/files             | POST /admin-api/gym/files（/upload 等价）      | gym:files:upload    |
 * | GET  /api/admin/v1/files/{id}        | GET  /admin-api/gym/files/{id}                | gym:files:read      |
 * | GET  /api/admin/v1/files/{id}/content| GET  /admin-api/gym/files/{id}/content        | gym:files:read      |
 *
 * 与旧 Mock 的差异：
 * 1. 旧 Mock 用 HTTP 状态码（201/415/404），本实现沿用 yudao 约定：HTTP 200 + body {@code code}；
 *    二进制内容接口例外，文件内容缺失时返回 HTTP 404。
 * 2. 旧 Mock 内容存内存、进程重启即失效；本实现复用 infra 文件服务（本地磁盘 / 数据库 / S3 等按 infra 配置）。
 * 3. 旧 Mock 返回文件记录时含存储地址；本实现不返回 infra 的 url，前端统一走 {@code /content} 鉴权下载。
 *
 * @author JSS
 */
@Tag(name = "管理后台 - gym 私有文件")
@RestController
@RequestMapping("/gym/files")
@Validated
public class GymFileController {

    @Resource private GymFileService gymFileService;

    @Resource private GlobalExceptionHandler globalExceptionHandler;

    @PostMapping({"", "/upload"})
    @Operation(summary = "上传私有文件", description = "魔数校验内容类型、按类别限制大小；storeId 为空时默认上传人所属门店")
    @Parameter(
            name = "file",
            description = "文件附件",
            required = true,
            schema = @Schema(type = "string", format = "binary"))
    @PreAuthorize("@ss.hasPermission('gym:files:upload')")
    public CommonResult<GymFileRespVO> uploadFile(@Valid GymFileUploadReqVO uploadReqVO) {
        GymFileDO file = gymFileService.uploadFile(uploadReqVO);
        return success(BeanUtils.toBean(file, GymFileRespVO.class));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得文件元数据")
    @Parameter(name = "id", description = "文件编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('gym:files:read')")
    public CommonResult<GymFileRespVO> getFile(@PathVariable("id") Long id) {
        GymFileDO file = gymFileService.getFile(id);
        return success(BeanUtils.toBean(file, GymFileRespVO.class));
    }

    @GetMapping("/{id}/content")
    @Operation(
            summary = "下载文件内容",
            description = "鉴权下载；文件或内容缺失返回 HTTP 404，权限不足返回 HTTP 403，不暴露 infra 的公开存储地址")
    @Parameter(name = "id", description = "文件编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('gym:files:read')")
    public void getFileContent(@PathVariable("id") Long id, HttpServletResponse response)
            throws IOException {
        GymFileContentDTO content = gymFileService.getFileContent(id);
        GymFileTypeUtils.writeAttachment(
                response, content.getName(), content.getMimeType(), content.getContent());
    }

    /** 保留框架错误码与日志；只有二进制下载使用非成功 HTTP 状态，避免 Blob 客户端误读错误 JSON。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult<?>> handleException(
            HttpServletRequest request, Exception exception) {
        CommonResult<?> result = globalExceptionHandler.allExceptionHandler(request, exception);
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null || !pattern.toString().endsWith("/{id}/content")) {
            return ResponseEntity.ok(result);
        }
        int status = getStatus(result);
        return ResponseEntity.status(status).body(result);
    }

    private static int getStatus(CommonResult<?> result) {
        int code;
        code = result.getCode();
        int status;
        if (code == FILE_NOT_EXISTS.getCode() || code == FILE_CONTENT_NOT_EXISTS.getCode()) {
            status = HttpServletResponse.SC_NOT_FOUND;
        } else if (code == FILE_STORE_NO_PERMISSION.getCode()) {
            status = HttpServletResponse.SC_FORBIDDEN;
        } else {
            status =
                    code >= 400 && code <= 599
                            ? code
                            : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return status;
    }
}
