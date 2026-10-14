package cn.iocoder.yudao.module.gym.enums.file;

import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * gym 文件类别枚举
 *
 * 对应旧 Mock（target/plan/10-backend-mock-api-inventory.md §4.1）：
 * category ∈ approval / body_report / contract / image / video，逐类限制大小与类型。
 *
 * 扩展方式：新增一个枚举项即可，Controller / Service 无需改动。
 *
 * @author JSS
 */
@Getter
@AllArgsConstructor
public enum GymFileCategoryEnum {

    /** 审批附件：合同/开卡/改价等审批的扫描件、照片 */
    APPROVAL("approval", "审批附件", 20L * 1024 * 1024,
            SetUtils.asSet(GymFileMimeTypeEnum.PDF, GymFileMimeTypeEnum.PNG, GymFileMimeTypeEnum.JPEG,
                    GymFileMimeTypeEnum.GIF, GymFileMimeTypeEnum.WEBP)),

    /** 体测报告：体测仪导出的 PDF，或手机拍摄的照片 */
    BODY_REPORT("body_report", "体测报告", 20L * 1024 * 1024,
            SetUtils.asSet(GymFileMimeTypeEnum.PDF, GymFileMimeTypeEnum.PNG, GymFileMimeTypeEnum.JPEG)),

    /** 合同文件：只允许 PDF，避免签章件被替换成图片 */
    CONTRACT("contract", "合同文件", 20L * 1024 * 1024,
            Collections.singleton(GymFileMimeTypeEnum.PDF)),

    /** 图片素材：门店环境图、活动图 */
    IMAGE("image", "图片素材", 10L * 1024 * 1024,
            SetUtils.asSet(GymFileMimeTypeEnum.PNG, GymFileMimeTypeEnum.JPEG,
                    GymFileMimeTypeEnum.GIF, GymFileMimeTypeEnum.WEBP)),

    /** 视频素材：门店环境视频（上限 200MB，需同步 spring.servlet.multipart.max-file-size） */
    VIDEO("video", "视频素材", 200L * 1024 * 1024,
            SetUtils.asSet(GymFileMimeTypeEnum.MP4, GymFileMimeTypeEnum.QUICKTIME));

    private final String category;
    private final String name;
    /** 该类别允许的最大字节数 */
    private final Long maxSize;
    /** 该类别允许的 MIME 类型（以魔数嗅探结果为准，不信任客户端声明） */
    private final Set<GymFileMimeTypeEnum> allowedMimeTypes;

    private static final Map<String, GymFileCategoryEnum> BY_CATEGORY = Stream.of(values())
            .collect(Collectors.toMap(GymFileCategoryEnum::getCategory, e -> e, (a, b) -> a, LinkedHashMap::new));

    /**
     * 按 category 字符串查找；不存在返回 null
     */
    public static GymFileCategoryEnum find(String category) {
        return category == null ? null : BY_CATEGORY.get(category);
    }

    /**
     * 该类别是否允许指定的 MIME 类型
     */
    public boolean isMimeTypeAllowed(String mimeType) {
        GymFileMimeTypeEnum mime = GymFileMimeTypeEnum.findByMimeType(mimeType);
        return mime != null && allowedMimeTypes.contains(mime);
    }

    /**
     * 全部合法 category 值（保持枚举声明顺序，保证报错信息稳定），用于校验报错提示
     */
    public static String allCategories() {
        return BY_CATEGORY.keySet().toString();
    }

}
