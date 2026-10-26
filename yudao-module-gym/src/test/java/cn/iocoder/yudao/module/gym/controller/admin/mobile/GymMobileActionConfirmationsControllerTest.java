package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymActionConfirmationVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileActionConfirmationsService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileActionConfirmationsControllerTest {
    GymMobileActionConfirmationsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileActionConfirmationsService.class);
        var controller = new GymMobileActionConfirmationsController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    ResultActions postBody(String body) throws Exception {
        return mvc.perform(
                post("/gym/mobile/action-confirmations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void requestPreservesLeadingZerosAndResponseContainsOnlyTokenAndExpiry() throws Exception {
        when(service.create(any()))
                .thenReturn(new Result("a".repeat(43), "2026-10-26T12:02:00+08:00"));
        var result =
                postBody(
                        "{\"action\":\"open_door\",\"resourceId\":7,\"verificationCode\":\"005924\"}");
        assertEquals(0, code(result));
        verify(service)
                .create(
                        argThat(
                                r ->
                                        r.getResourceId() == 7L
                                                && "005924".equals(r.getVerificationCode())));
        assertEquals(
                Set.of("confirmationToken", "expiresAt"),
                JsonUtils.parseObject(
                                result.andReturn().getResponse().getContentAsString(),
                                "data",
                                Map.class)
                        .keySet());
        assertEquals(
                "private, no-store", result.andReturn().getResponse().getHeader("Cache-Control"));
    }

    @Test
    void malformedMissingUnknownAndInjectedServerFieldsFailWithoutEcho() throws Exception {
        for (var body :
                List.of(
                        "{}",
                        "{bad",
                        "{\"action\":\"open_door\",\"resourceId\":0,\"verificationCode\":\"287082\"}",
                        "{\"action\":\"open_door\",\"resourceId\":7,\"verificationCode\":\"secret-should-not-echo\"}",
                        "{\"action\":\"open_door\",\"resourceId\":7,\"verificationCode\":\"287082\",\"userId\":1}")) {
            var result = postBody(body);
            assertEquals(400, code(result));
            assertFalse(
                    result.andReturn()
                            .getResponse()
                            .getContentAsString()
                            .contains("secret-should-not-echo"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void unexpectedFailureReturnsGenericErrorAndDisablesSensitiveAccessLogging() throws Exception {
        when(service.create(any())).thenThrow(new IllegalStateException("secret-should-not-echo"));
        var result =
                postBody(
                        "{\"action\":\"open_door\",\"resourceId\":7,\"verificationCode\":\"287082\"}");
        assertEquals(500, code(result));
        assertFalse(
                result.andReturn()
                        .getResponse()
                        .getContentAsString()
                        .contains("secret-should-not-echo"));
        var method =
                GymMobileActionConfirmationsController.class.getMethod(
                        "create", Request.class, jakarta.servlet.http.HttpServletResponse.class);
        var annotation = method.getAnnotation(ApiAccessLog.class);
        assertFalse(annotation.requestEnable());
        assertFalse(annotation.responseEnable());
        assertTrue(
                method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class)
                        .value()
                        .contains("gym:devices:command"));
    }
}
