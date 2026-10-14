package cn.iocoder.yudao.module.gym.framework.file.core.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.module.gym.enums.file.GymFileMimeTypeEnum;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * gym 文件类型工具：以「魔数」判定真实内容类型，不信任客户端的 Content-Type / 后缀名。
 *
 * 对应旧 Mock（target/plan/10-backend-mock-api-inventory.md §4.1）：sniff 魔数定 MIME，内容与类型不符 -> 415。
 *
 * 为什么不用文件名后缀：后缀可随意伪造（把 .exe 改名 .png 即可绕过），
 * 而审批附件、合同、体测报告都属于合规敏感数据，必须以内容为准。
 *
 * 扩展方式：在 {@link #detectMimeType(byte[])} 中补一条 {@code if (matchesAt/ startsWith)} 规则，
 * 并在 {@link GymFileMimeTypeEnum} 登记该类型。
 *
 * @author JSS
 */
@Slf4j
public class GymFileTypeUtils {

    private GymFileTypeUtils() {
    }

    /** PNG: 89 50 4E 47 0D 0A 1A 0A */
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    /** JPEG: FF D8 FF */
    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    /** GIF: GIF87a / GIF89a */
    private static final byte[] MAGIC_GIF87A = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MAGIC_GIF89A = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    /** WEBP: RIFF....WEBP */
    private static final byte[] MAGIC_RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MAGIC_WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
    /** PDF: %PDF */
    private static final byte[] MAGIC_PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);
    /** MP4 / MOV: [4 字节长度]ftyp[4 字节 brand] */
    private static final byte[] MAGIC_FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] BRAND_QUICKTIME = "qt  ".getBytes(StandardCharsets.US_ASCII);

    /**
     * 按魔数嗅探内容类型
     *
     * @param content 文件内容
     * @return MIME 类型；无法识别时返回 null
     */
    public static String detectMimeType(byte[] content) {
        GymFileMimeTypeEnum type = detect(content);
        return type == null ? null : type.getMimeType();
    }

    /**
     * 按魔数嗅探内容类型
     *
     * @param content 文件内容
     * @return 类型枚举；无法识别时返回 null
     */
    public static GymFileMimeTypeEnum detect(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        if (startsWith(content, MAGIC_PNG)) {
            return GymFileMimeTypeEnum.PNG;
        }
        if (startsWith(content, MAGIC_JPEG)) {
            return GymFileMimeTypeEnum.JPEG;
        }
        if (startsWith(content, MAGIC_GIF87A) || startsWith(content, MAGIC_GIF89A)) {
            return GymFileMimeTypeEnum.GIF;
        }
        // WEBP: 前 4 字节 RIFF，第 8~11 字节 WEBP
        if (startsWith(content, MAGIC_RIFF) && matchesAt(content, 8, MAGIC_WEBP)) {
            return GymFileMimeTypeEnum.WEBP;
        }
        if (startsWith(content, MAGIC_PDF)) {
            return GymFileMimeTypeEnum.PDF;
        }
        // MP4 / MOV 同属 ISO BMFF：第 4~7 字节为 ftyp，第 8~11 字节为 brand
        if (matchesAt(content, 4, MAGIC_FTYP)) {
            return matchesAt(content, 8, BRAND_QUICKTIME)
                    ? GymFileMimeTypeEnum.QUICKTIME : GymFileMimeTypeEnum.MP4;
        }
        return null;
    }

    /**
     * 按文件名后缀推断类型（仅用于与魔数结果比对，不能作为放行依据）
     *
     * @param fileName 文件名，允许为空
     * @return 类型枚举；无法识别时返回 null
     */
    public static GymFileMimeTypeEnum detectByFileName(String fileName) {
        if (StrUtil.isEmpty(fileName)) {
            return null;
        }
        return GymFileMimeTypeEnum.findByExtension(StrUtil.subAfter(fileName, ".", true));
    }

    /**
     * 输出文件内容：图片/视频 inline（便于前端预览），其余 attachment（强制下载）
     *
     * @param response 响应
     * @param filename 文件名（支持中文，已按 RFC 5987 编码）
     * @param mimeType MIME 类型
     * @param content  文件内容
     */
    public static void writeAttachment(HttpServletResponse response, String filename,
                                       String mimeType, byte[] content) throws IOException {
        response.setContentType(mimeType);
        GymFileMimeTypeEnum type = GymFileMimeTypeEnum.findByMimeType(mimeType);
        boolean inline = type != null && (type.isImage() || type.isVideo());
        response.setHeader("Content-Disposition",
                buildContentDisposition(inline ? "inline" : "attachment", filename));
        // 视频需要显式长度 + 允许分段，否则移动端播放器无法拖动进度
        if (type != null && type.isVideo()) {
            response.setHeader("Accept-Ranges", "bytes");
        }
        response.setHeader("Content-Length", String.valueOf(content.length));
        IoUtil.write(response.getOutputStream(), false, content);
    }

    private static String buildContentDisposition(String dispositionType, String filename) {
        return StrUtil.format("{};filename=\"{}\";filename*=UTF-8''{}",
                dispositionType, buildFallbackFilename(filename), HttpUtils.encodeUrlPathSegment(filename));
    }

    /** 非 ASCII 字符降级为 _，保证老浏览器也能拿到一个合法文件名 */
    private static String buildFallbackFilename(String filename) {
        if (StrUtil.isEmpty(filename)) {
            return "download";
        }
        StringBuilder result = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char ch = filename.charAt(i);
            if (ch == '"' || ch == '\\') {
                result.append('\\').append(ch);
            } else if (ch >= 0x20 && ch <= 0x7E) {
                result.append(ch);
            } else {
                result.append('_');
            }
        }
        return result.toString();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return matchesAt(content, 0, prefix);
    }

    private static boolean matchesAt(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (content[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

}
