package cn.iocoder.yudao.module.gym.service.device;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.ACTION_CONFIRMATION_UNAVAILABLE;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 4226 / 6238: SHA-1, six digits, 30 seconds; no external provider required. */
@Component
public class GymActionTotp {
    public static final class Credential {
        private final byte[] key;
        private final String fingerprint;

        private Credential(byte[] key) {
            this.key = key;
            this.fingerprint = hash(key);
        }

        public String fingerprint() {
            return fingerprint;
        }

        @Override
        public String toString() {
            return "Credential[redacted]";
        }
    }

    public Credential credential(String secret) {
        if (secret == null || !secret.matches("[A-Z2-7]{32,103}"))
            throw exception(ACTION_CONFIRMATION_UNAVAILABLE);
        byte[] bytes = new byte[secret.length() * 5 / 8];
        int buffer = 0, bits = 0, offset = 0;
        for (char c : secret.toCharArray()) {
            int value = c >= 'A' && c <= 'Z' ? c - 'A' : c - '2' + 26;
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                bytes[offset++] = (byte) (buffer >>> bits);
                buffer &= (1 << bits) - 1;
            }
        }
        if (bits >= 5 || buffer != 0 || bytes.length < 20 || bytes.length > 64)
            throw exception(ACTION_CONFIRMATION_UNAVAILABLE);
        return new Credential(bytes);
    }

    public Long match(Credential credential, String input, Instant now, Long lastCounter) {
        if (input == null || !input.matches("[0-9]{6}") || now.getEpochSecond() < 0) return null;
        long current = now.getEpochSecond() / 30;
        Long matched = null;
        for (long counter = Math.max(0, current - 1); counter <= current + 1; counter++) {
            if (MessageDigest.isEqual(
                    code(credential, counter).getBytes(StandardCharsets.US_ASCII),
                    input.getBytes(StandardCharsets.US_ASCII))) {
                // An old accepted code must not be accepted again even if adjacent counters
                // collide.
                if (lastCounter != null && counter <= lastCounter) return null;
                matched = counter;
            }
        }
        return matched;
    }

    String code(Credential credential, long counter) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(credential.key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = h[h.length - 1] & 15;
            int value =
                    ((h[offset] & 127) << 24)
                            | ((h[offset + 1] & 255) << 16)
                            | ((h[offset + 2] & 255) << 8)
                            | (h[offset + 3] & 255);
            return String.format(Locale.ROOT, "%06d", value % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP cryptography unavailable");
        }
    }

    public static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
