package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileApprovalsVO.Approval;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCardIssueService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileCardIssueControllerTest {
    GymMobileCardIssueService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileCardIssueService.class);
        var c = new GymMobileCardIssueController();
        ReflectionTestUtils.setField(c, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(c)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "card-issue", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    String body() {
        return "{\"storeId\":101,\"memberId\":7,\"productType\":\"course\",\"productId\":8,\"reason\":\"补发审核\",\"approvalTemplateId\":3}";
    }

    @Test
    void bindsProductAndWorkflowAndReturnsPendingApprovalWithoutIssuingRights() throws Exception {
        when(service.create(any(), eq("issue-test-key")))
                .thenReturn(
                        new Approval(
                                "9",
                                "card_opening",
                                "101",
                                "1",
                                "pending",
                                1,
                                1,
                                Map.of("productType", "course"),
                                List.of(),
                                "2026-06-10T10:00+08:00"));
        var r =
                mvc.perform(
                        post("/gym/mobile/card-issue-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "issue-test-key")
                                .content(body()));
        assertEquals(0, code(r));
        verify(service)
                .create(
                        argThat(
                                q ->
                                        "course".equals(q.getProductType())
                                                && q.getMemberId() == 7L
                                                && q.getProductId() == 8L
                                                && q.getApprovalTemplateId() == 3L),
                        eq("issue-test-key"));
        assertTrue(
                r.andReturn()
                        .getResponse()
                        .getContentAsString()
                        .contains("\"status\":\"pending\""));
    }

    @Test
    void rejectsMissingKeyInvalidProductAndInjectedPriceOrApprovalStatus() throws Exception {
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/mobile/card-issue-applications")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body()))));
        for (String content :
                List.of(
                        body().replace("course", "private"),
                        body().replace("补发审核", " "),
                        body().replace("}", ",\"amount\":0}"),
                        body().replace("}", ",\"status\":\"approved\"}")))
            assertNotEquals(
                    0,
                    code(
                            mvc.perform(
                                    post("/gym/mobile/card-issue-applications")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header("Idempotency-Key", "issue-test-key")
                                            .content(content))));
        verifyNoInteractions(service);
    }
}
