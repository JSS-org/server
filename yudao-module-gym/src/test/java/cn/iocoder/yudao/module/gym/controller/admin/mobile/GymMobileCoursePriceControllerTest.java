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
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCoursePriceService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileCoursePriceControllerTest {
    GymMobileCoursePriceService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileCoursePriceService.class);
        var c = new GymMobileCoursePriceController();
        ReflectionTestUtils.setField(c, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(c)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "course-price", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    String body() {
        return "{\"storeId\":101,\"memberId\":7,\"proposedAmount\":\"150.00\",\"productId\":8,\"reason\":\"补发审核\",\"approvalTemplateId\":3}";
    }

    @Test
    void bindsProductAndWorkflowAndReturnsPendingApprovalWithoutIssuingRights() throws Exception {
        when(service.create(any(), eq("price-test-key")))
                .thenReturn(
                        new Approval(
                                "9",
                                "price_change",
                                "101",
                                "1",
                                "pending",
                                1,
                                1,
                                Map.of("priceScope", "quote"),
                                List.of(),
                                "2026-06-10T10:00+08:00"));
        var r =
                mvc.perform(
                        post("/gym/mobile/course-price-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "price-test-key")
                                .content(body()));
        assertEquals(0, code(r));
        verify(service)
                .create(
                        argThat(
                                q ->
                                        new java.math.BigDecimal("150.00")
                                                        .equals(q.getProposedAmount())
                                                && q.getMemberId() == 7L
                                                && q.getProductId() == 8L
                                                && q.getApprovalTemplateId() == 3L),
                        eq("price-test-key"));
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
                                post("/gym/mobile/course-price-applications")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body()))));
        for (String content :
                List.of(
                        body().replace("150.00", "-1.00"),
                        body().replace("150.00", "1.001"),
                        body().replace("150.00", "10000000000.00"),
                        body().replace("150.00", "bad"),
                        body().replace("补发审核", " "),
                        body().replace("}", ",\"amount\":0}"),
                        body().replace("}", ",\"status\":\"approved\"}")))
            assertNotEquals(
                    0,
                    code(
                            mvc.perform(
                                    post("/gym/mobile/course-price-applications")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header("Idempotency-Key", "price-test-key")
                                            .content(content))));
        verifyNoInteractions(service);
    }
}
