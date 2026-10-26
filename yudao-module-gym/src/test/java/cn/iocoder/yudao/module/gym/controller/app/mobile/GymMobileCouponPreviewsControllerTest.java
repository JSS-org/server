package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponPreviewVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCouponPreviewsService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileCouponPreviewsControllerTest {
    GymMobileCouponPreviewsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileCouponPreviewsService.class);
        var controller = new AppGymCouponPreviewsController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "coupon", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    String call(String body) throws Exception {
        return mvc.perform(
                        post("/gym/coupon-verification-previews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void bindsPrivateCodeWithoutLosingLeadingZerosAndReturnsFivePublicFields() throws Exception {
        when(service.preview(any()))
                .thenReturn(
                        new Preview(
                                "9007199254740993", "团购卡", true, null, "2026-10-26T12:05+08:00"));
        var body =
                call(
                        "{\"storeId\":\"101\",\"platform\":\"meituan\",\"couponCode\":\"0012345678901\"}");
        assertEquals(0, JsonUtils.parseObject(body, "code", Integer.class));
        verify(service)
                .preview(
                        argThat(
                                r ->
                                        r.getStoreId() == 101L
                                                && r.getCouponCode().equals("0012345678901")));
        var data = JsonUtils.parseObject(body, "data", Map.class);
        assertEquals(
                Set.of("previewId", "productName", "redeemable", "reason", "expiresAt"),
                data.keySet());
        assertEquals("9007199254740993", data.get("previewId"));
        assertFalse(body.contains("0012345678901"));
    }

    @Test
    void invalidOrCallerSuppliedIdentityFieldsNeverReachService() throws Exception {
        for (var body :
                List.of(
                        "{}",
                        "{\"storeId\":-1,\"platform\":\"meituan\",\"couponCode\":\"123\"}",
                        "{\"storeId\":101,\"platform\":\"douyin\",\"couponCode\":\"123\"}",
                        "{\"storeId\":101,\"platform\":\"meituan\",\"couponCode\":\"a b\"}"))
            assertEquals(400, JsonUtils.parseObject(call(body), "code", Integer.class));
        assertEquals(
                1070023005,
                JsonUtils.parseObject(
                        call(
                                "{\"storeId\":101,\"platform\":\"meituan\",\"couponCode\":\"123\",\"memberId\":99}"),
                        "code",
                        Integer.class));
        verifyNoInteractions(service);
    }

    @Test
    void providerOrOwnershipFailureRemainsBusinessError() throws Exception {
        when(service.preview(any()))
                .thenThrow(
                        new cn.iocoder.yudao.framework.common.exception.ServiceException(
                                cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants
                                        .COUPON_OWNERSHIP_REQUIRED));
        var body =
                call("{\"storeId\":101,\"platform\":\"meituan\",\"couponCode\":\"0012345678901\"}");
        assertEquals(1070032003, JsonUtils.parseObject(body, "code", Integer.class));
        assertNull(JsonUtils.parseObject(body, "data", Map.class));
        assertFalse(body.contains("0012345678901"));
    }
}
