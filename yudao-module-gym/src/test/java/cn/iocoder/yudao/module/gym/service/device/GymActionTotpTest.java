package cn.iocoder.yudao.module.gym.service.device;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

class GymActionTotpTest {
    static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    final GymActionTotp totp = new GymActionTotp();

    @ParameterizedTest
    @CsvSource({
        "59,287082",
        "1111111109,081804",
        "1111111111,050471",
        "1234567890,005924",
        "2000000000,279037",
        "20000000000,353130"
    })
    void rfc6238Sha1VectorsReducedToSixDigits(long seconds, String expected) {
        var key = totp.credential(SECRET);
        assertEquals(expected, totp.code(key, seconds / 30));
        assertEquals(seconds / 30, totp.match(key, expected, Instant.ofEpochSecond(seconds), null));
    }

    @Test
    void windowIsExactlyOneStepAndAcceptedCodesCannotReplay() {
        var key = totp.credential(SECRET);
        assertEquals(1L, totp.match(key, "287082", Instant.ofEpochSecond(0), null));
        assertEquals(1L, totp.match(key, "287082", Instant.ofEpochSecond(89), null));
        assertNull(totp.match(key, "287082", Instant.ofEpochSecond(90), null));
        assertNull(totp.match(key, "287082", Instant.ofEpochSecond(59), 1L));
        assertNull(totp.match(key, "287082", Instant.ofEpochSecond(59), 2L));
        assertNull(totp.match(key, "287082", Instant.ofEpochSecond(-1), null));
    }

    @Test
    void malformedSecretsAndCodesAreRejectedWithoutEchoingCredentials() {
        for (var secret :
                java.util.List.of(
                        "",
                        SECRET.toLowerCase(),
                        SECRET + "=",
                        " " + SECRET,
                        SECRET + "A",
                        SECRET + "AB",
                        SECRET.substring(1),
                        "A".repeat(104))) {
            var e = assertThrows(ServiceException.class, () -> totp.credential(secret));
            assertFalse(e.getMessage().contains(SECRET));
        }
        assertThrows(ServiceException.class, () -> totp.credential(null));
        for (var code : java.util.List.of("28708", "2870820", " 287082", "２８７０８２", "abcdef"))
            assertNull(totp.match(totp.credential(SECRET), code, Instant.ofEpochSecond(59), null));
        assertFalse(totp.credential(SECRET).toString().contains(SECRET));
        assertEquals(64, totp.credential(SECRET).fingerprint().length());
    }

    @Test
    void acceptedAdjacentCounterCollisionCannotReplaySameDigits() {
        var stub =
                new GymActionTotp() {
                    @Override
                    String code(Credential key, long counter) {
                        return "123456";
                    }
                };
        assertNull(stub.match(stub.credential(SECRET), "123456", Instant.ofEpochSecond(60), 1L));
        assertEquals(
                3L, stub.match(stub.credential(SECRET), "123456", Instant.ofEpochSecond(60), null));
    }
}
