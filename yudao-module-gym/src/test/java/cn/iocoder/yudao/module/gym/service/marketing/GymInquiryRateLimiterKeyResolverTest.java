package cn.iocoder.yudao.module.gym.service.marketing;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;

class GymInquiryRateLimiterKeyResolverTest {
    private final GymInquiryRateLimiterKeyResolver resolver =
            new GymInquiryRateLimiterKeyResolver();

    private String key(String peer, String forwarded, String body) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwarded);
        request.addHeader("Idempotency-Key", body);
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return resolver.resolver(null, null);
    }

    @AfterEach
    void clean() {
        TenantContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requestBodyKeyAndUntrustedForwardingHeadersCannotBypassLimit() {
        TenantContextHolder.setTenantId(1L);
        assertEquals(
                key("127.0.0.1", "1.2.3.4", "request-a"), key("127.0.0.1", "9.8.7.6", "request-b"));
    }

    @Test
    void distinctTransportPeersAndTenantsHaveIndependentLimits() {
        TenantContextHolder.setTenantId(1L);
        String first = key("127.0.0.1", "ignored", "a");
        assertNotEquals(first, key("127.0.0.2", "ignored", "a"));
        TenantContextHolder.setTenantId(2L);
        assertNotEquals(first, key("127.0.0.1", "ignored", "a"));
    }
}
