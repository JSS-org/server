package cn.iocoder.yudao.framework.apilog.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.apilog.core.filter.ApiAccessLogFilter;
import cn.iocoder.yudao.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.nio.charset.StandardCharsets;
import java.util.Map;

class ApiAccessLogSanitizationTest {
    static class Controller {
        public void login() {}

        @ApiAccessLog(sanitizeKeys = {"privateNote"})
        public void custom() {}

        @ApiAccessLog(requestEnable = false)
        public void noParameters() {}
    }

    private String console(String method, MockHttpServletRequest request) throws Exception {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        var interceptorLogger = (Logger) LoggerFactory.getLogger(ApiAccessLogInterceptor.class);
        var filterLogger = (Logger) LoggerFactory.getLogger(ApiAccessLogFilter.class);
        var jsonLogger = (Logger) LoggerFactory.getLogger(JsonUtils.class);
        interceptorLogger.addAppender(appender);
        filterLogger.addAppender(appender);
        jsonLogger.addAppender(appender);
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(SpringUtils::isProd).thenReturn(false);
            var handler = new HandlerMethod(new Controller(), Controller.class.getMethod(method));
            new ApiAccessLogInterceptor()
                    .preHandle(request, new MockHttpServletResponse(), handler);
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + b);
        } finally {
            interceptorLogger.detachAppender(appender);
            filterLogger.detachAppender(appender);
            jsonLogger.detachAppender(appender);
            appender.stop();
        }
    }

    private MockHttpServletRequest json(String body) {
        var request = new MockHttpServletRequest("POST", "/app-api/member/auth/login");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    void consoleRemovesNestedPasswordAndTokenVariantsButKeepsOrdinaryFields() throws Exception {
        String output =
                console(
                        "login",
                        json(
                                """
                {"mobile":"fixture-account","password":"private-password","nested":[{"access_token":"private-token","ClientSecret":"private-client"}]}
                """));
        assertTrue(output.contains("fixture-account"));
        assertFalse(output.contains("private-password"));
        assertFalse(output.contains("private-token"));
        assertFalse(output.contains("private-client"));
    }

    @Test
    void consoleRedactsRefreshQueryWithoutChangingTheControllerParameters() throws Exception {
        var request = new MockHttpServletRequest("POST", "/app-api/member/auth/refresh-token");
        request.addParameter("refreshToken", "private-refresh");
        request.addParameter("pageNo", "2");
        String output = console("login", request);
        assertFalse(output.contains("private-refresh"));
        assertTrue(output.contains("pageNo"));
        assertEquals("private-refresh", request.getParameter("refreshToken"));
    }

    @Test
    void malformedBodyIsOmittedFromBothTheRequestAndSanitizerErrorLog() throws Exception {
        String output = console("login", json("{\"password\":\"private-malformed"));
        assertFalse(output.contains("private-malformed"));
        assertNull(ApiAccessLogFilter.sanitizeJson("{\"password\":\"private-malformed", null));
    }

    @Test
    void consoleHonorsCustomKeysAndDisabledRequestLogging() throws Exception {
        assertFalse(
                console("custom", json("{\"privateNote\":\"private-note\",\"name\":\"visible\"}"))
                        .contains("private-note"));
        assertFalse(
                console("noParameters", json("{\"name\":\"private-disabled\"}"))
                        .contains("private-disabled"));
    }

    @Test
    void persistedQuerySanitizingDoesNotMutateItsInput() {
        var original = Map.of("refresh_token", "private-refresh", "pageNo", "2");
        String output = ApiAccessLogFilter.sanitizeMap(original, null);
        assertFalse(output.contains("private-refresh"));
        assertTrue(output.contains("pageNo"));
        assertEquals(2, original.size());
    }

    @Test
    void optedInResponseLoggingRedactsNestedCredentialsAndSupportsNullData() {
        String output =
                ReflectionTestUtils.invokeMethod(
                        ApiAccessLogFilter.class,
                        "sanitizeJson",
                        CommonResult.success(
                                Map.of(
                                        "accessToken",
                                        "private-access",
                                        "user",
                                        Map.of("newPassword", "private-new", "id", 3))),
                        new String[0]);
        assertNotNull(output);
        assertFalse(output.contains("private-access"));
        assertFalse(output.contains("private-new"));
        assertTrue(output.contains("\"id\":3"));
        assertNotNull(
                ReflectionTestUtils.invokeMethod(
                        ApiAccessLogFilter.class,
                        "sanitizeJson",
                        CommonResult.success(null),
                        new String[0]));
    }
}
