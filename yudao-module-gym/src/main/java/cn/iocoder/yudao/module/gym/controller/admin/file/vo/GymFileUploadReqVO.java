package cn.iocoder.yudao.module.gym.controller.admin.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理后台 - gym 文件上传 Request VO（multipart/form-data）
 *
 * 对应旧 Mock：{@code POST /api/admin/v1/files}（file + category + storeId）
 *
 * @author JSS
 */
@Schema(description = "管理后台 - gym 文件上传 Request VO")
@Data
public class GymFileUploadReqVO {

    @Schema(description = "文件附件", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件附件不能为空")
    private MultipartFile file;

    @Schema(description = "文件类别", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "approval", allowableValues = {"approval", "body_report", "training_plan", "contract", "image", "video"})
    @NotNull(message = "文件类别不能为空")
    private String category;

    @Schema(description = "归属门店编号（不传则取当前登录人所属部门）", example = "124")
    private Long storeId;

}
