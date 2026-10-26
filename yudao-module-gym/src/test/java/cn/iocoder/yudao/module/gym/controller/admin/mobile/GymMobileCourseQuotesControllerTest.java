package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.AppGymCourseQuotesController;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCourseQuoteVO.Quote;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCourseQuoteService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileCourseQuotesControllerTest {
    GymMobileCourseQuoteService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileCourseQuoteService.class);
        var staff = new GymMobileCourseQuotesController();
        var member = new AppGymCourseQuotesController();
        ReflectionTestUtils.setField(staff, "service", service);
        ReflectionTestUtils.setField(member, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(staff, member)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "course-quotes", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    String body() {
        return "{\"storeId\":101,\"productId\":8,\"memberId\":7,\"approvalId\":3}";
    }

    Quote quote() {
        return new Quote(
                "9",
                "101",
                "8",
                "7",
                "150.00",
                "2026-10-26T12:00+08:00",
                "active",
                "/pages/course-quote/detail?quoteId=9");
    }

    @Test
    void bindsScopeWithoutClientMoneyAndReturnsSafeQuoteContract() throws Exception {
        when(service.create(any(), eq("quote-test-key"))).thenReturn(quote());
        var r =
                mvc.perform(
                        post("/gym/mobile/course-quotes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "quote-test-key")
                                .content(body()));
        assertEquals(0, code(r));
        verify(service)
                .create(
                        argThat(
                                q ->
                                        q.getStoreId() == 101L
                                                && q.getProductId() == 8L
                                                && q.getMemberId() == 7L
                                                && q.getApprovalId() == 3L),
                        eq("quote-test-key"));
        assertTrue(
                r.andReturn().getResponse().getContentAsString().contains("\"amount\":\"150.00\""));
    }

    @Test
    void rejectsMissingKeyAndInjectedPriceExpiryOrApprovedStatus() throws Exception {
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/mobile/course-quotes")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body()))));
        for (String text :
                List.of(
                        body().replace("101", "0"),
                        body().replace("}", ",\"amount\":1}"),
                        body().replace("}", ",\"expiresAt\":\"2099-01-01\"}"),
                        body().replace("}", ",\"status\":\"approved\"}")))
            assertNotEquals(
                    0,
                    code(
                            mvc.perform(
                                    post("/gym/mobile/course-quotes")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header("Idempotency-Key", "quote-test-key")
                                            .content(text))));
        verifyNoInteractions(service);
    }

    @Test
    void readsByQuoteIdWithoutAcceptingMemberIdentityFromRequest() throws Exception {
        when(service.get(9L)).thenReturn(quote());
        var r = mvc.perform(get("/gym/course-quotes/9").param("memberId", "999"));
        assertEquals(0, code(r));
        verify(service).get(9L);
        String json = r.andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("approvalId"));
        assertFalse(json.contains("comment"));
        assertTrue(json.contains("\"memberId\":\"7\""));
    }
}
