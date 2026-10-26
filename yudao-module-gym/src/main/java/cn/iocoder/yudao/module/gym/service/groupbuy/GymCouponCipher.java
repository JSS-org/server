package cn.iocoder.yudao.module.gym.service.groupbuy;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileCouponProperties;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.*;

@Component
public class GymCouponCipher {
    @Resource private GymMobileCouponProperties properties;
    private static final SecureRandom RANDOM = new SecureRandom();

    private byte[] key(String purpose) {
        try {
            byte[] master = Base64.getDecoder().decode(properties.getEncryptionKey());
            if (master.length != 32) throw new IllegalArgumentException();
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(("gym:coupon:v1:" + purpose).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw exception(COUPON_NOT_CONFIGURED);
        }
    }

    public void requireConfigured() {
        key("encrypt");
    }

    public String hash(Long tenant, String platform, String code) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key("lookup"), "HmacSHA256"));
            return HexFormat.of()
                    .formatHex(
                            mac.doFinal(
                                    (tenant + ":" + platform + ":" + code)
                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw exception(COUPON_NOT_CONFIGURED);
        }
    }

    private byte[] aad(Long tenant, Long store, Long user) {
        return ("gym:coupon:v1:" + tenant + ":" + store + ":" + user)
                .getBytes(StandardCharsets.UTF_8);
    }

    public String seal(String code, Long tenant, Long store, Long user) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key("encrypt"), "AES"),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(tenant, store, user));
            return "v1."
                    + Base64.getEncoder().encodeToString(iv)
                    + "."
                    + Base64.getEncoder()
                            .encodeToString(cipher.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw exception(COUPON_NOT_CONFIGURED);
        }
    }

    public String open(String value, Long tenant, Long store, Long user) {
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException();
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            if (iv.length != 12) throw new IllegalArgumentException();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key("encrypt"), "AES"),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(tenant, store, user));
            return new String(
                    cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw exception(COUPON_PREVIEW_INVALID);
        }
    }
}
