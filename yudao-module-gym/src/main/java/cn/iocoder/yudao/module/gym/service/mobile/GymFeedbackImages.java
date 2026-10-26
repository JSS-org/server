package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.FEEDBACK_ATTACHMENT_INVALID;

import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Staff receive decoded/re-encoded pixels, never the original filename or embedded metadata. */
@Component
public class GymFeedbackImages {
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    public GymFileContentDTO sanitize(byte[] bytes, String mime) {
        if (bytes == null
                || bytes.length == 0
                || bytes.length > MAX_BYTES
                || mime == null
                || !Set.of("image/png", "image/jpeg").contains(mime))
            throw exception(FEEDBACK_ATTACHMENT_INVALID);
        var type = GymFileTypeUtils.detect(bytes);
        if (type == null || !mime.equals(type.getMimeType()))
            throw exception(FEEDBACK_ATTACHMENT_INVALID);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var candidates = ImageIO.getImageReaders(input);
            if (!candidates.hasNext()) throw exception(FEEDBACK_ATTACHMENT_INVALID);
            var reader = candidates.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0
                        || height <= 0
                        || width > 8192
                        || height > 8192
                        || (long) width * height > 20000000)
                    throw exception(FEEDBACK_ATTACHMENT_INVALID);
                var original = reader.read(0);
                if (original == null) throw exception(FEEDBACK_ATTACHMENT_INVALID);
                var safe =
                        new BufferedImage(
                                width,
                                height,
                                "image/png".equals(mime)
                                        ? BufferedImage.TYPE_INT_ARGB
                                        : BufferedImage.TYPE_INT_RGB);
                var graphics = safe.createGraphics();
                try {
                    graphics.drawImage(original, 0, 0, null);
                } finally {
                    graphics.dispose();
                    original.flush();
                }
                try (var output = new ByteArrayOutputStream()) {
                    String extension = "image/png".equals(mime) ? "png" : "jpg";
                    if (!ImageIO.write(safe, extension, output) || output.size() > MAX_BYTES)
                        throw exception(FEEDBACK_ATTACHMENT_INVALID);
                    return new GymFileContentDTO(
                            "feedback-image." + extension, mime, output.toByteArray());
                } finally {
                    safe.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw exception(FEEDBACK_ATTACHMENT_INVALID);
        }
    }
}
