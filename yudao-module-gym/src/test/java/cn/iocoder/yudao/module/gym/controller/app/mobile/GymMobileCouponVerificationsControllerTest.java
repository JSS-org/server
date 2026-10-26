package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponVerificationVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCouponVerificationsService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileCouponVerificationsControllerTest {
    GymMobileCouponVerificationsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileCouponVerificationsService.class);
        var c = new AppGymCouponVerificationsController();
        ReflectionTestUtils.setField(c, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(c)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "coupon-verification", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void createBindsPreviewAndKeyAndExposesOnlyFourResultFields() throws Exception {
        when(service.create(any(), anyString()))
                .thenReturn(new Result("99", "succeeded", List.of("9007199254740993"), null));
        var r =
                mvc.perform(
                        post("/gym/coupon-verifications")
                                .header("Idempotency-Key", "redeem-test-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"previewId\":12}"));
        assertEquals(0, code(r));
        verify(service).create(argThat(q -> q.getPreviewId() == 12L), eq("redeem-test-key"));
        var data =
                JsonUtils.parseObject(
                        r.andReturn().getResponse().getContentAsString(), "data", Map.class);
        assertEquals(Set.of("id", "status", "memberCardIds", "failureReason"), data.keySet());
        assertEquals(List.of("9007199254740993"), data.get("memberCardIds"));
    }

    @Test
    void missingHeaderInvalidBodyAndMissingIdsAreRejectedBeforeService() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/coupon-verifications")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"previewId\":12}"))));
        for (var body : List.of("{}", "{\"previewId\":0}", "{\"previewId\":-1}"))
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    post("/gym/coupon-verifications")
                                            .header("Idempotency-Key", "redeem-test-key")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))));
        verifyNoInteractions(service);
    }

    @Test
    void getReturnsProcessingAndCompensatedFailedWithoutInternalErrorOrOwnerFields()
            throws Exception {
        for (var status : List.of("processing", "failed")) {
            when(service.get(99L)).thenReturn(new Result("99", status, List.of(), "待核实"));
            var r = mvc.perform(get("/gym/me/coupon-verifications/99").param("memberId", "123"));
            assertEquals(0, code(r));
            var data =
                    JsonUtils.parseObject(
                            r.andReturn().getResponse().getContentAsString(), "data", Map.class);
            assertEquals(Set.of("id", "status", "memberCardIds", "failureReason"), data.keySet());
            assertEquals(status, data.get("status"));
            assertEquals(List.of(), data.get("memberCardIds"));
        }
        verify(service, times(2)).get(99L);
    }

    @Test
    void unknownIdentityAndResultFieldsCannotBeInjected() throws Exception {
        for (String field : List.of("memberId", "status", "memberCardIds", "providerRequestId")) {
            assertEquals(
                    1070023005,
                    code(
                            mvc.perform(
                                    post("/gym/coupon-verifications")
                                            .header("Idempotency-Key", "coupon-extra-key")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    "{\"previewId\":12,\""
                                                            + field
                                                            + "\":\"forged\"}"))));
        }
        verifyNoInteractions(service);
    }
}
