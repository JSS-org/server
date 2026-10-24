package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.GymMobileLessonQrController;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileLessonVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileLessonService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileLessonControllerTest {
    static final String ISSUE = "/gym/mobile/lesson-qr-codes",
            CONFIRM = "/gym/lesson-confirmations";
    GymMobileLessonService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileLessonService.class);
        var a = new GymMobileLessonQrController();
        var b = new AppGymLessonConfirmationController();
        ReflectionTestUtils.setField(a, "service", service);
        ReflectionTestUtils.setField(b, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(a, b)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "mobile-lesson-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions action) throws Exception {
        return JsonUtils.parseObject(
                action.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    ResultActions call(String path, String body) throws Exception {
        return mvc.perform(
                post(path)
                        .header("Idempotency-Key", "lesson-http-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void routesBindOnlySessionOrOwnedBookingAndToken() throws Exception {
        assertEquals(0, code(call(ISSUE, "{\"sessionId\":6}")));
        verify(service).issue(argThat(r -> r.getSessionId() == 6L), eq("lesson-http-key"));
        assertEquals(0, code(call(CONFIRM, "{\"bookingId\":7,\"qrToken\":\"short-test-token\"}")));
        verify(service)
                .confirm(
                        argThat(
                                r ->
                                        r.getBookingId() == 7L
                                                && r.getQrToken().equals("short-test-token")),
                        eq("lesson-http-key"));
    }

    @Test
    void headersRequiredAndCannotForgeCheckedInMemberSourceOrDeduction() throws Exception {
        for (String path : List.of(ISSUE, CONFIRM))
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    post(path)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{}"))));
        assertEquals(400, code(call(ISSUE, "{\"sessionId\":0}")));
        assertEquals(400, code(call(CONFIRM, "{\"bookingId\":7,\"qrToken\":\"\"}")));
        assertEquals(
                400,
                code(call(CONFIRM, "{\"bookingId\":7,\"qrToken\":\"" + "x".repeat(129) + "\"}")));
        for (String field : List.of("checkedIn", "memberId", "source", "deductedLessons"))
            assertEquals(
                    MOBILE_REQUEST_INVALID.getCode(),
                    code(
                            call(
                                    CONFIRM,
                                    "{\"bookingId\":7,\"qrToken\":\"token\",\""
                                            + field
                                            + "\":1}")));
        assertEquals(
                MOBILE_REQUEST_INVALID.getCode(),
                code(call(ISSUE, "{\"sessionId\":6,\"expiresAt\":\"2099\"}")));
        verifyNoInteractions(service);
    }

    @Test
    void failureEnvelopeAndAccessLogConfigurationDoNotExposeQrPayload() throws Exception {
        when(service.confirm(any(), anyString()))
                .thenThrow(new ServiceException(MOBILE_LESSON_QR_INVALID));
        assertEquals(
                MOBILE_LESSON_QR_INVALID.getCode(),
                code(call(CONFIRM, "{\"bookingId\":7,\"qrToken\":\"token\"}")));
        var annotation =
                GymMobileLessonQrController.class
                        .getMethod("create", QrCreate.class, String.class)
                        .getAnnotation(
                                cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog
                                        .class);
        assertFalse(annotation.requestEnable());
        assertFalse(annotation.responseEnable());
        annotation =
                AppGymLessonConfirmationController.class
                        .getMethod("confirm", Confirm.class, String.class)
                        .getAnnotation(
                                cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog
                                        .class);
        assertFalse(annotation.requestEnable());
        assertFalse(annotation.responseEnable());
    }
}
