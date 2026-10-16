package cn.iocoder.yudao.module.gym.enums.file;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * gym 支持的文件 MIME 类型
 *
 * 只登记「有明确魔数、且业务上需要」的类型，避免像通用文件服务那样放开全部后缀。
 * 扩展方式：新增枚举项 + 在 {@link cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils}
 * 中补一条魔数规则（若是新家族）。
 *
 * @author JSS
 */
@Getter
@AllArgsConstructor
public enum GymFileMimeTypeEnum {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf"),
    MP4("video/mp4", "mp4"),
    QUICKTIME("video/quicktime", "mov");

    private final String mimeType;

    /** 规范扩展名，用于补全缺失后缀 */
    private final String extension;

    private static final Map<String, GymFileMimeTypeEnum> BY_MIME_TYPE =
            Stream.of(values()).collect(Collectors.toMap(GymFileMimeTypeEnum::getMimeType, e -> e));

    /** 扩展名 -> 类型；同一类型只登记规范扩展名与常见别名 */
    private static final Map<String, GymFileMimeTypeEnum> BY_EXTENSION =
            Stream.of(
                            new Object[][] {
                                {"png", PNG},
                                {"jpg", JPEG},
                                {"jpeg", JPEG},
                                {"gif", GIF},
                                {"webp", WEBP},
                                {"pdf", PDF},
                                {"mp4", MP4},
                                {"mov", QUICKTIME}
                            })
                    .collect(
                            Collectors.toMap(
                                    row -> (String) row[0], row -> (GymFileMimeTypeEnum) row[1]));

    public static GymFileMimeTypeEnum findByMimeType(String mimeType) {
        return mimeType == null ? null : BY_MIME_TYPE.get(mimeType.trim().toLowerCase());
    }

    /**
     * 按扩展名推断类型（用于与「魔数嗅探结果」做一致性比对，不作为放行依据）
     */
    public static GymFileMimeTypeEnum findByExtension(String extension) {
        return extension == null ? null : BY_EXTENSION.get(extension.trim().toLowerCase());
    }

    public boolean isImage() {
        return mimeType.startsWith("image/");
    }

    public boolean isVideo() {
        return mimeType.startsWith("video/");
    }

    public static String allMimeTypes() {
        return Arrays.toString(Stream.of(values()).map(GymFileMimeTypeEnum::getMimeType).toArray());
    }
}
