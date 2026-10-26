package cn.iocoder.yudao.module.gym.service;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.FEEDBACK_ATTACHMENT_INVALID;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.gym.service.mobile.GymFeedbackImages;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.*;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;

class GymFeedbackImagesTest {
    GymFeedbackImages images = new GymFeedbackImages();

    @Test
    void decodesPixelsAndStripsEmbeddedIdentityMetadataForStaffPreview() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff00ff00);
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        var metadata =
                writer.getDefaultImageMetadata(
                        ImageTypeSpecifier.createFromRenderedImage(image),
                        writer.getDefaultWriteParam());
        var root = new IIOMetadataNode("javax_imageio_png_1.0");
        var text = new IIOMetadataNode("tEXt");
        var entry = new IIOMetadataNode("tEXtEntry");
        entry.setAttribute("keyword", "Comment");
        entry.setAttribute("value", "private-member-13800002401");
        text.appendChild(entry);
        root.appendChild(text);
        metadata.mergeTree("javax_imageio_png_1.0", root);
        var bytes = new ByteArrayOutputStream();
        try (var out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, metadata), writer.getDefaultWriteParam());
        } finally {
            writer.dispose();
        }
        assertTrue(
                new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)
                        .contains("private-member-13800002401"));
        var safe = images.sanitize(bytes.toByteArray(), "image/png");
        assertEquals("feedback-image.png", safe.getName());
        assertFalse(
                new String(safe.getContent(), java.nio.charset.StandardCharsets.ISO_8859_1)
                        .contains("private-member-13800002401"));
        assertEquals(
                0xff00ff00, ImageIO.read(new ByteArrayInputStream(safe.getContent())).getRGB(0, 0));
        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        assertEquals(
                "feedback-image.jpg", images.sanitize(jpeg.toByteArray(), "image/jpeg").getName());
    }

    @Test
    void rejectsInvalidMimeBrokenImageAndUnsafeDecodeDimensionsBeforeReturningContent()
            throws Exception {
        assertServiceException(
                () -> images.sanitize(null, "image/png"), FEEDBACK_ATTACHMENT_INVALID);
        assertServiceException(
                () -> images.sanitize(new byte[11 * 1024 * 1024], "image/png"),
                FEEDBACK_ATTACHMENT_INVALID);
        assertServiceException(
                () ->
                        images.sanitize(
                                new byte[] {
                                    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1
                                },
                                "image/png"),
                FEEDBACK_ATTACHMENT_INVALID);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8193, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
        assertServiceException(
                () -> images.sanitize(bytes.toByteArray(), "image/png"),
                FEEDBACK_ATTACHMENT_INVALID);
        assertServiceException(
                () -> images.sanitize(bytes.toByteArray(), "image/jpeg"),
                FEEDBACK_ATTACHMENT_INVALID);
        assertServiceException(
                () -> images.sanitize(bytes.toByteArray(), null), FEEDBACK_ATTACHMENT_INVALID);
    }
}
