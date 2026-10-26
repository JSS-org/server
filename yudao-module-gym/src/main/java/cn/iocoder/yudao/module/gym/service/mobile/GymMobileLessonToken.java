package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileLessonProperties;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class GymMobileLessonToken {
    private static final SecureRandom RANDOM = new SecureRandom();
    @Resource private GymMobileLessonProperties properties;

    public String create() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return "v1."
                + nonce
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(nonce));
    }

    public void verify(String token) {
        if (token == null || !token.matches("v1\\.[A-Za-z0-9_-]{43}\\.[A-Za-z0-9_-]{43}"))
            throw exception(MOBILE_LESSON_QR_INVALID);
        var parts = token.split("\\.");
        if (!MessageDigest.isEqual(sign(parts[1]), Base64.getUrlDecoder().decode(parts[2])))
            throw exception(MOBILE_LESSON_QR_INVALID);
    }

    private byte[] sign(String nonce) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(properties.getQrSigningKey());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw exception(MOBILE_LESSON_QR_UNAVAILABLE);
        }
        if (secret.length < 32) throw exception(MOBILE_LESSON_QR_UNAVAILABLE);
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(
                    ("gym-lesson-qr:v1:" + TenantContextHolder.getRequiredTenantId() + ":" + nonce)
                            .getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
