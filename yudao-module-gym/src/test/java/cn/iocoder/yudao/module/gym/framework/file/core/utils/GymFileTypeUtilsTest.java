package cn.iocoder.yudao.module.gym.framework.file.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.iocoder.yudao.module.gym.enums.file.GymFileCategoryEnum;
import cn.iocoder.yudao.module.gym.enums.file.GymFileMimeTypeEnum;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * {@link GymFileTypeUtils} 的单元测试：验证魔数嗅探只认内容、不认后缀
 *
 * @author JSS
 */
public class GymFileTypeUtilsTest {

    /** 合法 PNG 头 + 填充 */
    private static byte[] png() {
        return concat(
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
                "filler-bytes".getBytes());
    }

    private static byte[] jpeg() {
        return concat(
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0},
                "filler-bytes".getBytes());
    }

    private static byte[] gif() {
        return concat("GIF89a".getBytes(StandardCharsets.US_ASCII), "filler-bytes".getBytes());
    }

    private static byte[] webp() {
        return concat(
                concat("RIFF".getBytes(StandardCharsets.US_ASCII), new byte[] {0, 0, 0, 0}),
                concat("WEBP".getBytes(StandardCharsets.US_ASCII), "filler-bytes".getBytes()));
    }

    private static byte[] pdf() {
        return concat("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), "filler-bytes".getBytes());
    }

    /** ftyp box：4 字节长度 + ftyp + brand + 填充 */
    private static byte[] ftyp(String brand) {
        return concat(
                new byte[] {0, 0, 0, 0x18},
                concat(
                        "ftyp".getBytes(StandardCharsets.US_ASCII),
                        concat(
                                brand.getBytes(StandardCharsets.US_ASCII),
                                "filler-bytes".getBytes())));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    @Test
    public void testDetectMimeType() {
        assertEquals("image/png", GymFileTypeUtils.detectMimeType(png()));
        assertEquals("image/jpeg", GymFileTypeUtils.detectMimeType(jpeg()));
        assertEquals("image/gif", GymFileTypeUtils.detectMimeType(gif()));
        assertEquals("image/webp", GymFileTypeUtils.detectMimeType(webp()));
        assertEquals("application/pdf", GymFileTypeUtils.detectMimeType(pdf()));
        assertEquals("video/mp4", GymFileTypeUtils.detectMimeType(ftyp("isom")));
        assertEquals("video/quicktime", GymFileTypeUtils.detectMimeType(ftyp("qt  ")));
    }

    @Test
    public void testDetectMimeType_unrecognized() {
        assertNull(GymFileTypeUtils.detectMimeType(null));
        assertNull(GymFileTypeUtils.detectMimeType(new byte[0]));
        assertNull(
                GymFileTypeUtils.detectMimeType(
                        "MZ\u0000\u0000executable".getBytes(StandardCharsets.US_ASCII)));
        // RIFF 但不是 WEBP（例如 WAV）
        assertNull(
                GymFileTypeUtils.detectMimeType(
                        concat(
                                "RIFF".getBytes(StandardCharsets.US_ASCII),
                                concat(
                                        new byte[] {0, 0, 0, 0},
                                        "WAVE".getBytes(StandardCharsets.US_ASCII)))));
    }

    @Test
    public void testDetect_ignoresFileExtension() {
        // 关键安全用例：后缀是 .png，内容其实是 PDF -> 嗅探结果必须是 PDF
        assertEquals(GymFileMimeTypeEnum.PDF, GymFileTypeUtils.detect(pdf()));
        assertEquals(GymFileMimeTypeEnum.PNG, GymFileTypeUtils.detectByFileName("photo.png"));
        assertNull(GymFileTypeUtils.detectByFileName("no-extension"));
        assertNull(GymFileTypeUtils.detectByFileName(null));
    }

    @Test
    public void testCategoryMimeTypeWhitelist() {
        // 合同只允许 PDF
        assertTrue(GymFileCategoryEnum.CONTRACT.isMimeTypeAllowed("application/pdf"));
        assertFalse(GymFileCategoryEnum.CONTRACT.isMimeTypeAllowed("image/png"));
        // 图片素材允许 PNG/JPEG/GIF/WEBP，不允许 PDF/视频
        assertTrue(GymFileCategoryEnum.IMAGE.isMimeTypeAllowed("image/webp"));
        assertFalse(GymFileCategoryEnum.IMAGE.isMimeTypeAllowed("application/pdf"));
        assertFalse(GymFileCategoryEnum.IMAGE.isMimeTypeAllowed("video/mp4"));
        // 视频素材允许 MP4/MOV
        assertTrue(GymFileCategoryEnum.VIDEO.isMimeTypeAllowed("video/quicktime"));
        assertFalse(GymFileCategoryEnum.VIDEO.isMimeTypeAllowed("image/png"));
    }

    @Test
    public void testCategorySizeLimit() {
        assertEquals(10L * 1024 * 1024, GymFileCategoryEnum.IMAGE.getMaxSize());
        assertEquals(20L * 1024 * 1024, GymFileCategoryEnum.APPROVAL.getMaxSize());
        assertEquals(200L * 1024 * 1024, GymFileCategoryEnum.VIDEO.getMaxSize());
        assertNull(GymFileCategoryEnum.find("coupon"));
        assertEquals(GymFileCategoryEnum.BODY_REPORT, GymFileCategoryEnum.find("body_report"));
    }
}
