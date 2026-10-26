package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.security.TenantSecurityWebFilter;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.web.TenantContextWebFilter;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.framework.mobile.*;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class GymPublicMediaTenantFilterTest {
    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void queryTenantIsLimitedToExactPublicGetRouteAndHeaderRemainsAuthoritative() throws Exception {
        for (String path :
                List.of(
                        "/app-api/gym/stores",
                        "/app-api/gym/files/7/content",
                        "/admin-api/gym/public-media/7/content",
                        "/app-api/gym/public-media/7/content/extra")) {
            var request = new MockHttpServletRequest("GET", path);
            request.setParameter("tenantId", "5");
            new GymPublicMediaTenantFilter("/app-api")
                    .doFilter(
                            request,
                            new MockHttpServletResponse(),
                            (r, s) -> assertNull(((HttpServletRequest) r).getHeader("tenant-id")));
        }
        var request = new MockHttpServletRequest("GET", "/app-api/gym/public-media/7/content");
        request.setParameter("tenantId", "5");
        request.addHeader("tenant-id", "6");
        new GymPublicMediaTenantFilter("/app-api")
                .doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (r, s) ->
                                assertEquals("6", ((HttpServletRequest) r).getHeader("tenant-id")));
        request = new MockHttpServletRequest("POST", "/app-api/gym/public-media/7/content");
        request.setParameter("tenantId", "5");
        new GymPublicMediaTenantFilter("/app-api")
                .doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (r, s) -> assertNull(((HttpServletRequest) r).getHeader("tenant-id")));
    }

    @Test
    void selectorRejectsAmbiguousOverflowOrNonNumericValuesAndSupportsPositiveLongIds()
            throws Exception {
        for (String[] values :
                List.of(
                        new String[] {"-1"},
                        new String[] {"0"},
                        new String[] {"1", "2"},
                        new String[] {"9223372036854775808"},
                        new String[] {"1 OR 1=1"})) {
            var request = new MockHttpServletRequest("GET", "/app-api/gym/public-media/7/content");
            request.setParameter("tenantId", values);
            new GymPublicMediaTenantFilter("/app-api")
                    .doFilter(
                            request,
                            new MockHttpServletResponse(),
                            (r, s) -> assertNull(((HttpServletRequest) r).getHeader("tenant-id")));
        }
        var request =
                new MockHttpServletRequest("GET", "/prefix/app-api/gym/public-media/7/content");
        request.setContextPath("/prefix");
        request.setParameter("tenantId", Long.toString(Long.MAX_VALUE));
        new GymPublicMediaTenantFilter("/app-api")
                .doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (r, s) -> {
                            var http = (HttpServletRequest) r;
                            assertEquals(
                                    Long.toString(Long.MAX_VALUE), http.getHeader("Tenant-Id"));
                            assertEquals(
                                    List.of(Long.toString(Long.MAX_VALUE)),
                                    Collections.list(http.getHeaders("tenant-id")));
                            assertTrue(
                                    Collections.list(http.getHeaderNames()).contains("tenant-id"));
                        });
    }

    @Test
    void selectedTenantStillPassesExistingValidationAndRejectsLoggedInTenantMismatch()
            throws Exception {
        var web = new WebProperties();
        var tenants = mock(TenantFrameworkService.class);
        var handler = new GlobalExceptionHandler("tenant-test", mock(ApiErrorLogCommonApi.class));
        var security =
                new TenantSecurityWebFilter(
                        web, new TenantProperties(), Set.of(), handler, tenants);
        var reached = new AtomicReference<Long>();
        var request = new MockHttpServletRequest("GET", "/app-api/gym/public-media/7/content");
        request.setParameter("tenantId", "5");
        var response = new MockHttpServletResponse();
        new GymPublicMediaTenantFilter("/app-api")
                .doFilter(
                        request,
                        response,
                        (r, s) ->
                                new TenantContextWebFilter()
                                        .doFilter(
                                                r,
                                                s,
                                                (r2, s2) ->
                                                        security.doFilter(
                                                                r2,
                                                                s2,
                                                                (r3, s3) ->
                                                                        reached.set(
                                                                                TenantContextHolder
                                                                                        .getRequiredTenantId()))));
        assertEquals(5L, reached.get());
        verify(tenants).validTenant(5L);
        assertNull(TenantContextHolder.getTenantId());
        assertTrue(
                new GymMobileWebConfiguration().gymPublicMediaTenantSelector(web).getOrder()
                        < cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum
                                .TENANT_CONTEXT_FILTER);
        var login = new LoginUser();
        login.setId(1L);
        login.setUserType(1);
        login.setTenantId(6L);
        SecurityFrameworkUtils.setLoginUser(login, request);
        reached.set(null);
        response = new MockHttpServletResponse();
        new GymPublicMediaTenantFilter("/app-api")
                .doFilter(
                        request,
                        response,
                        (r, s) ->
                                new TenantContextWebFilter()
                                        .doFilter(
                                                r,
                                                s,
                                                (r2, s2) ->
                                                        security.doFilter(
                                                                r2,
                                                                s2,
                                                                (r3, s3) ->
                                                                        reached.set(
                                                                                TenantContextHolder
                                                                                        .getRequiredTenantId()))));
        assertNull(reached.get());
        assertEquals(
                403, JsonUtils.parseObject(response.getContentAsString(), "code", Integer.class));
    }

    @Test
    void expiredTenantIsRejectedByExistingTenantSecurityFilter() throws Exception {
        var tenants = mock(TenantFrameworkService.class);
        var denied =
                cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                        cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_EXPIRE,
                        "test");
        doThrow(denied).when(tenants).validTenant(5L);
        var handler = new GlobalExceptionHandler("tenant-test", mock(ApiErrorLogCommonApi.class));
        var security =
                new TenantSecurityWebFilter(
                        new WebProperties(), new TenantProperties(), Set.of(), handler, tenants);
        var request = new MockHttpServletRequest("GET", "/app-api/gym/public-media/7/content");
        request.setParameter("tenantId", "5");
        var response = new MockHttpServletResponse();
        new GymPublicMediaTenantFilter("/app-api")
                .doFilter(
                        request,
                        response,
                        (r, s) ->
                                new TenantContextWebFilter()
                                        .doFilter(
                                                r,
                                                s,
                                                (r2, s2) ->
                                                        security.doFilter(
                                                                r2,
                                                                s2,
                                                                (r3, s3) ->
                                                                        fail(
                                                                                "Expired tenant must not reach the media service"))));
        assertEquals(
                denied.getCode(),
                JsonUtils.parseObject(response.getContentAsString(), "code", Integer.class));
        assertNull(TenantContextHolder.getTenantId());
    }
}
