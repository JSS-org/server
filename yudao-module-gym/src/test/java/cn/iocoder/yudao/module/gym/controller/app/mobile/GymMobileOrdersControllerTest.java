package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileOrdersVO.Order;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileOrdersService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileOrdersControllerTest {
    GymMobileOrdersService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileOrdersService.class);
        var controller = new AppGymOrdersController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "member-orders", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    String body() {
        return "{\"storeId\":101,\"productType\":\"course\",\"productId\":8,\"quantity\":1,\"quoteId\":7}";
    }

    Order order() {
        return new Order(
                "9",
                "G-test",
                "101",
                "course",
                "8",
                "私教",
                1,
                "150.00",
                "0.00",
                "0.00",
                "unpaid",
                "pending",
                "2026-10-25T12:30+08:00",
                List.of(),
                List.of());
    }

    @Test
    void createBindsProductQuoteAndQuantityAndReturnsMoneyAsStrings() throws Exception {
        when(service.create(any(), eq("member-order-key"))).thenReturn(order());
        var r =
                mvc.perform(
                        post("/gym/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "member-order-key")
                                .content(body()));
        assertEquals(0, code(r));
        verify(service)
                .create(
                        argThat(
                                q ->
                                        q.getStoreId() == 101L
                                                && q.getProductId() == 8L
                                                && q.getQuoteId() == 7L
                                                && q.getQuantity() == 1
                                                && "course".equals(q.getProductType())),
                        eq("member-order-key"));
        assertTrue(
                r.andReturn()
                        .getResponse()
                        .getContentAsString()
                        .contains("\"payableAmount\":\"150.00\""));
    }

    @Test
    void rejectsMissingKeyUnsupportedSubscriptionQuantityAndInjectedIdentityMoneyOrState()
            throws Exception {
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/orders")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body()))));
        for (String text :
                List.of(
                        body().replace("course", "subscription"),
                        body().replace("\"quantity\":1", "\"quantity\":2"),
                        body().replace("}", ",\"memberId\":7}"),
                        body().replace("}", ",\"payableAmount\":1}"),
                        body().replace("}", ",\"status\":\"paid\"}")))
            assertNotEquals(
                    0,
                    code(
                            mvc.perform(
                                    post("/gym/orders")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header("Idempotency-Key", "member-order-key")
                                            .content(text))));
        verifyNoInteractions(service);
    }

    @Test
    void bindsOwnedListDetailAndCancellationWithoutTakingCallerSuppliedMember() throws Exception {
        when(service.page(any())).thenReturn(new PageResult<>(List.of(order()), 1L));
        when(service.get(9L)).thenReturn(order());
        when(service.cancel(eq(9L), any(), eq("cancel-order-key"))).thenReturn(order());
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/me/orders")
                                        .param("pageNo", "2")
                                        .param("status", "unpaid")
                                        .param("memberId", "999"))));
        verify(service).page(argThat(q -> q.getPageNo() == 2 && "unpaid".equals(q.getStatus())));
        assertEquals(0, code(mvc.perform(get("/gym/me/orders/9"))));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/orders/9/cancellations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", "cancel-order-key")
                                        .content("{\"reason\":\"计划改变\"}"))));
        verify(service)
                .cancel(eq(9L), argThat(q -> "计划改变".equals(q.getReason())), eq("cancel-order-key"));
        assertNotEquals(0, code(mvc.perform(get("/gym/me/orders").param("status", "other"))));
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/orders/9/cancellations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", "cancel-order-key")
                                        .content("{\"amount\":0}"))));
    }
}
