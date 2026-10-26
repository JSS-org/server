package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointRedemptionVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePointRedemptionsService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobilePointRedemptionsControllerTest {
    GymMobilePointRedemptionsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobilePointRedemptionsService.class);
        var c = new AppGymPointRedemptionsController();
        ReflectionTestUtils.setField(c, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(c)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "point-redemption", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void createBindsProductStoreAndKeyAndExposesOnlyFourResultFields() throws Exception {
        when(service.create(any(), anyString()))
                .thenReturn(new Result("99", "succeeded", 100, "88"));
        var r =
                mvc.perform(
                        post("/gym/point-redemptions")
                                .header("Idempotency-Key", "redeem-test-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"storeId\":101,\"productId\":12}"));
        assertEquals(0, code(r));
        verify(service)
                .create(
                        argThat(q -> q.getStoreId() == 101L && q.getProductId() == 12L),
                        eq("redeem-test-key"));
        var data =
                JsonUtils.parseObject(
                        r.andReturn().getResponse().getContentAsString(), "data", Map.class);
        assertEquals(Set.of("id", "status", "spentPoints", "memberCardId"), data.keySet());
        assertEquals("88", data.get("memberCardId"));
    }

    @Test
    void missingHeaderInvalidBodyAndMissingIdsAreRejectedBeforeService() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/point-redemptions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"storeId\":101,\"productId\":12}"))));
        for (var body :
                List.of(
                        "{}",
                        "{\"storeId\":0,\"productId\":12}",
                        "{\"storeId\":101,\"productId\":-1}"))
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    post("/gym/point-redemptions")
                                            .header("Idempotency-Key", "redeem-test-key")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))));
        verifyNoInteractions(service);
    }

    @Test
    void getReturnsProcessingAndCompensatedFailedWithoutInternalErrorOrOwnerFields()
            throws Exception {
        for (var status : List.of("processing", "failed")) {
            when(service.get(99L))
                    .thenReturn(new Result("99", status, status.equals("failed") ? 0 : 100, null));
            var r = mvc.perform(get("/gym/me/point-redemptions/99").param("memberId", "123"));
            assertEquals(0, code(r));
            var data =
                    JsonUtils.parseObject(
                            r.andReturn().getResponse().getContentAsString(), "data", Map.class);
            assertEquals(Set.of("id", "status", "spentPoints", "memberCardId"), data.keySet());
            assertEquals(status, data.get("status"));
            assertNull(data.get("memberCardId"));
        }
        verify(service, times(2)).get(99L);
    }
}
