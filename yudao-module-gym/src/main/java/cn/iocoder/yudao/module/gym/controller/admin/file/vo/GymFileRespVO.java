package cn.iocoder.yudao.module.gym.controller.admin.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - gym 文件 Response VO
 *
 * 安全约定：**不返回** infra 的存储 url。
 * 该 url 指向 infra 的 {@code @PermitAll} 下载接口，一旦泄露即等同于绕过了门店越权校验；
 * 前端一律使用本 VO 的 id 调 {@code GET /admin-api/gym/files/{id}/content} 鉴权下载。
 *
 * @author JSS
 */
@Schema(description = "管理后台 - gym 文件 Response VO")
@Data
public class GymFileRespVO {

    @Schema(description = "文件编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private String id;

    @Schema(description = "归属门店编号（system_dept.id）", example = "124")
    private String storeId;

    @Schema(description = "文件类别", example = "contract")
    private String category;

    @Schema(description = "原始文件名", example = "入会协议_张三.pdf")
    private String name;

    @Schema(description = "内容类型（魔数嗅探结果）", example = "application/pdf")
    private String mimeType;

    @Schema(description = "文件大小（字节）", example = "102400")
    private Long size;

    @Schema(description = "上传人编号", example = "252")
    private String uploaderId;

    @Schema(description = "上传时间")
    private LocalDateTime createTime;

    @Schema(description = "前端契约：原始文件名（兼容 name）")
    public String getOriginalName() {
        return name;
    }

    @Schema(description = "前端契约：文件字节数（兼容 size）")
    public Long getByteSize() {
        return size;
    }
}
