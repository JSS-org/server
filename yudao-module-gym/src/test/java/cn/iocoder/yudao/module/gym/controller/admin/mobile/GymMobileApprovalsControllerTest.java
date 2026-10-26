package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileApprovalsVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileApprovalsService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileApprovalsControllerTest {
    GymMobileApprovalsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileApprovalsService.class);
        var c = new GymMobileApprovalsController();
        ReflectionTestUtils.setField(c, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(c)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "approvals", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    Approval approval() {
        return new Approval(
                "7",
                "card_opening",
                "101",
                "1",
                "pending",
                2,
                2,
                Map.of("amount", "50.00"),
                List.of(new Step(1, "91", "approved", "同意", "2026-06-10T10:00+08:00")),
                "2026-06-10T09:00+08:00");
    }

    @Test
    void bindsViewsDetailsAndOptionsAndSerializesSafeApprovalProjection() throws Exception {
        when(service.page(any())).thenReturn(new PageResult<>(List.of(approval()), 1L));
        when(service.get(7L)).thenReturn(approval());
        when(service.options(any()))
                .thenReturn(
                        new Options(List.of(new Template("3", "审批", 1)), List.of("card_opening")));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/approvals")
                                        .param("view", "pending")
                                        .param("storeId", "101")
                                        .param("status", "pending"))));
        verify(service).page(argThat(q -> "pending".equals(q.getView()) && q.getStoreId() == 101L));
        String json =
                mvc.perform(get("/gym/mobile/approvals/7"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertTrue(json.contains("\"version\":2"));
        assertTrue(json.contains("\"amount\":\"50.00\""));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/approval-options")
                                        .param("storeId", "101")
                                        .param("businessType", "card_opening"))));
    }

    @Test
    void bindsRequiredDecisionVersionAndKeyAndRejectsUnknownBusinessInstructions()
            throws Exception {
        when(service.decide(eq(7L), any(), eq("approval-test-key"))).thenReturn(approval());
        String body = "{\"decision\":\"approve\",\"expectedVersion\":1,\"comment\":\"同意\"}";
        assertEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/mobile/approvals/7/decisions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", "approval-test-key")
                                        .content(body))));
        verify(service)
                .decide(
                        eq(7L),
                        argThat(
                                r ->
                                        r.getExpectedVersion() == 1
                                                && "approve".equals(r.getDecision())),
                        eq("approval-test-key"));
        clearInvocations(service);
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/mobile/approvals/7/decisions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))));
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/mobile/approvals/7/decisions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", "approval-test-key")
                                        .content(
                                                body.replace(
                                                        "}",
                                                        ",\"businessSummary\":{\"amount\":0}}")))));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingViewUnknownStatusInvalidOptionsAndMissingVersion() throws Exception {
        assertNotEquals(0, code(mvc.perform(get("/gym/mobile/approvals"))));
        assertNotEquals(0, code(mvc.perform(get("/gym/mobile/approvals").param("view", "all"))));
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/approvals")
                                        .param("view", "mine")
                                        .param("pageSize", "201"))));
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/approval-options")
                                        .param("businessType", "unknown")
                                        .param("storeId", "0"))));
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/mobile/approvals/7/decisions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", "approval-test-key")
                                        .content("{\"decision\":\"approve\"}"))));
        verifyNoInteractions(service);
    }
}
