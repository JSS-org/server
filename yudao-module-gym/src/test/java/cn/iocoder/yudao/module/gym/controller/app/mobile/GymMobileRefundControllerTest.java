package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.sales.GymRefundApplicationsController;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRefundVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.sales.GymRefundApplicationService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileRefundControllerTest {
    GymRefundApplicationService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymRefundApplicationService.class);
        var member = new AppGymRefundApplicationsController();
        var admin = new GymRefundApplicationsController();
        ReflectionTestUtils.setField(member, "service", service);
        ReflectionTestUtils.setField(admin, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(member, admin)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "refund-applications", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    Review review() {
        return new Review(
                "7",
                "9",
                "101",
                "20",
                "pending_review",
                "99.99",
                "0.00",
                "退费",
                null,
                1,
                "2026-10-25T10:00+08:00",
                null,
                null);
    }

    @Test
    void memberCreateAcceptsOnlyReasonAndUsesIdempotencyHeaderWithStringMoney() throws Exception {
        when(service.create(eq(9L), any(), eq("refund-mvc-key")))
                .thenReturn(new Submitted("7", "pending_review", "99.99"));
        var result =
                mvc.perform(
                        post("/gym/orders/9/refund-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "refund-mvc-key")
                                .content("{\"reason\":\"退费\"}"));
        assertEquals(0, code(result));
        verify(service)
                .create(eq(9L), argThat(x -> "退费".equals(x.getReason())), eq("refund-mvc-key"));
        assertTrue(
                result.andReturn()
                        .getResponse()
                        .getContentAsString()
                        .contains("\"requestedAmount\":\"99.99\""));
    }

    @Test
    void memberUnknownAmountsIdentityStateMissingHeaderAndBlankReasonAreRejected()
            throws Exception {
        for (String body :
                List.of(
                        "{\"reason\":\"退费\",\"amount\":1}",
                        "{\"reason\":\"退费\",\"memberId\":20}",
                        "{\"reason\":\"退费\",\"status\":\"refunded\"}",
                        "{\"reason\":\"  \"}")) {
            assertNotEquals(
                    0,
                    code(
                            mvc.perform(
                                    post("/gym/orders/9/refund-applications")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header("Idempotency-Key", "refund-mvc-key")
                                            .content(body))));
        }
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/orders/9/refund-applications")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"reason\":\"退费\"}"))));
        verifyNoInteractions(service);
    }

    @Test
    void memberReadReturnsOnlyApplicationProgressWithoutInternalReviewerOrProvider()
            throws Exception {
        when(service.get(7L))
                .thenReturn(new Application("7", "9", "approved", "99.99", "0.00", "退费", null));
        var result = mvc.perform(get("/gym/me/refund-applications/7").param("memberId", "999"));
        assertEquals(0, code(result));
        verify(service).get(7L);
        String json = result.andReturn().getResponse().getContentAsString();
        assertTrue(json.contains("\"refundedAmount\":\"0.00\""));
        assertFalse(json.contains("reviewerUserId"));
        assertFalse(json.contains("provider"));
    }

    @Test
    void adminPageDetailAndDecisionBindVersionAndExplicitDecisionRatherThanSuccessState()
            throws Exception {
        when(service.page(any())).thenReturn(new PageResult<>(List.of(review()), 1L));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/refund-applications")
                                        .param("storeId", "101")
                                        .param("status", "pending_review"))));
        verify(service)
                .page(
                        argThat(
                                q ->
                                        q.getStoreId().equals(101L)
                                                && q.getStatus().equals("pending_review")));
        when(service.review(7L)).thenReturn(review());
        assertEquals(0, code(mvc.perform(get("/gym/refund-applications/7"))));
        when(service.decide(eq(7L), any(), eq("refund-review-mvc"))).thenReturn(review());
        assertEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/refund-applications/7/decisions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", "refund-review-mvc")
                                        .content(
                                                "{\"expectedVersion\":1,\"decision\":\"approve\",\"note\":\"核实通过\"}"))));
        verify(service)
                .decide(
                        eq(7L),
                        argThat(
                                q ->
                                        q.getExpectedVersion() == 1
                                                && q.getDecision().equals("approve")
                                                && q.getNote().equals("核实通过")),
                        eq("refund-review-mvc"));
    }

    @Test
    void adminCannotSubmitRefundedStateAmountOrOmitExpectedVersion() throws Exception {
        for (String body :
                List.of(
                        "{\"expectedVersion\":1,\"decision\":\"refunded\",\"note\":\"完成\"}",
                        "{\"expectedVersion\":1,\"decision\":\"approve\",\"note\":\"通过\",\"amount\":99}",
                        "{\"decision\":\"approve\",\"note\":\"通过\"}")) {
            assertNotEquals(
                    0,
                    code(
                            mvc.perform(
                                    post("/gym/refund-applications/7/decisions")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header("Idempotency-Key", "refund-review-mvc")
                                            .content(body))));
        }
        verifyNoInteractions(service);
    }
}
