package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.GymMobileFeedbackController;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileFeedbackVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileFeedbackService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileFeedbackControllerTest {
    GymMobileFeedbackService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileFeedbackService.class);
        var a = new AppGymFeedbackController();
        var b = new GymMobileFeedbackController();
        var errors = new GlobalExceptionHandler("feedback-http", mock(ApiErrorLogCommonApi.class));
        ReflectionTestUtils.setField(a, "service", service);
        ReflectionTestUtils.setField(b, "service", service);
        ReflectionTestUtils.setField(b, "exceptionHandler", errors);
        mvc =
                MockMvcBuilders.standaloneSetup(a, b)
                        .setControllerAdvice(new GymMobileExceptionAdvice(), errors)
                        .build();
    }

    int code(ResultActions a) throws Exception {
        return JsonUtils.parseObject(
                a.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    ResultActions submit(String body) throws Exception {
        return mvc.perform(
                post("/gym/feedbacks")
                        .header("Idempotency-Key", "feedback-http-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void bindsFourTargetsAndStoreResponseContainsNoRestrictedIdentity() throws Exception {
        assertEquals(
                0,
                code(
                        submit(
                                "{\"storeId\":101,\"type\":\"review\",\"rating\":5,\"content\":\"很好\",\"allowCardContext\":false}")));
        verify(service)
                .submit(
                        argThat(
                                r ->
                                        r.getStoreId() == 101L
                                                && r.getRating() == 5
                                                && !r.getAllowCardContext()),
                        eq("feedback-http-key"));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/me/feedbacks")
                                        .param("storeId", "101")
                                        .param("memberId", "999"))));
        verify(service).mine(argThat(r -> r.getStoreId() == 101L));
        when(service.store(any()))
                .thenReturn(
                        new PageResult<>(
                                List.of(
                                        new StoreFeedback(
                                                "7",
                                                "review",
                                                5,
                                                "很好",
                                                List.of("8"),
                                                "received",
                                                "2026-10-24T20:00+08:00",
                                                null,
                                                null)),
                                1L));
        var result =
                mvc.perform(
                        get("/gym/mobile/feedbacks")
                                .param("storeId", "101")
                                .param("type", "review"));
        assertEquals(0, code(result));
        var data =
                JsonUtils.parseObject(
                        result.andReturn().getResponse().getContentAsString(), "data", Map.class);
        var row = (Map<?, ?>) ((List<?>) data.get("list")).getFirst();
        assertEquals(
                Set.of(
                        "id",
                        "type",
                        "rating",
                        "content",
                        "fileIds",
                        "status",
                        "createdAt",
                        "cardContext",
                        "reply"),
                row.keySet());
        assertEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/mobile/feedbacks/7/replies")
                                        .header("Idempotency-Key", "feedback-reply-http")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"content\":\"谢谢建议\"}"))));
        verify(service)
                .reply(
                        eq(7L),
                        argThat(r -> r.getContent().equals("谢谢建议")),
                        eq("feedback-reply-http"));
    }

    @Test
    void validatesKeyLimitsAndRejectsForgedMemberReplyAndConsentFields() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/feedbacks")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))));
        for (String body :
                List.of(
                        "{}",
                        "{\"storeId\":0,\"type\":\"feedback\",\"content\":\"test\"}",
                        "{\"storeId\":101,\"type\":\"review\",\"rating\":6,\"content\":\"test\"}",
                        "{\"storeId\":101,\"type\":\"feedback\",\"content\":\" \"}"))
            assertEquals(400, code(submit(body)));
        for (String field : List.of("memberId", "status", "cardContext", "reply"))
            assertEquals(
                    MOBILE_REQUEST_INVALID.getCode(),
                    code(
                            submit(
                                    "{\"storeId\":101,\"type\":\"feedback\",\"content\":\"test\",\""
                                            + field
                                            + "\":1}")));
        assertEquals(400, code(mvc.perform(get("/gym/mobile/feedbacks"))));
        assertEquals(400, code(mvc.perform(get("/gym/me/feedbacks").param("pageSize", "201"))));
        verifyNoInteractions(service);
    }

    @Test
    void attachmentHasSafeDispositionNoCacheAndRealFailureStatusAndBodiesAreNotLogged()
            throws Exception {
        when(service.attachment(7L, 8L))
                .thenReturn(
                        new GymFileContentDTO(
                                "feedback-image.png", "image/png", new byte[] {1, 2}));
        var response =
                mvc.perform(get("/gym/mobile/feedbacks/7/attachments/8/content"))
                        .andReturn()
                        .getResponse();
        assertEquals(200, response.getStatus());
        assertTrue(response.getHeader("Content-Disposition").contains("feedback-image.png"));
        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertArrayEquals(new byte[] {1, 2}, response.getContentAsByteArray());
        when(service.attachment(7L, 8L)).thenThrow(new ServiceException(STORE_NO_PERMISSION));
        response =
                mvc.perform(get("/gym/mobile/feedbacks/7/attachments/8/content"))
                        .andReturn()
                        .getResponse();
        assertEquals(403, response.getStatus());
        assertTrue(response.getHeader("Cache-Control").contains("no-store"));
        doThrow(new ServiceException(FILE_NOT_EXISTS)).when(service).attachment(7L, 8L);
        assertEquals(
                404,
                mvc.perform(get("/gym/mobile/feedbacks/7/attachments/8/content"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        for (var method :
                List.of(
                        AppGymFeedbackController.class.getMethod(
                                "submit", Submit.class, String.class),
                        GymMobileFeedbackController.class.getMethod(
                                "reply", Long.class, Reply.class, String.class))) {
            var annotation =
                    method.getAnnotation(
                            cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog.class);
            assertFalse(annotation.requestEnable());
            assertFalse(annotation.responseEnable());
        }
    }
}
