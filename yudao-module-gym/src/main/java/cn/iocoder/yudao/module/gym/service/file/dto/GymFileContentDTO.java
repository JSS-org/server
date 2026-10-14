package cn.iocoder.yudao.module.gym.service.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * gym 文件内容 DTO
 *
 * 用途：内容流接口需要「内容 + 下载所需的元数据」，一次性返回，避免 Controller 重复查询（也重复做越权校验）。
 *
 * @author JSS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymFileContentDTO {

    /** 原始文件名（用于 Content-Disposition） */
    private String name;

    /** 内容类型（魔数嗅探结果，用于 Content-Type） */
    private String mimeType;

    /** 文件内容 */
    private byte[] content;

}
