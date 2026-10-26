package cn.iocoder.yudao.module.gym.controller.admin.groupbuy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.GymMeituanCouponHistoryRespVO;
import cn.iocoder.yudao.module.gym.service.groupbuy.GymGroupBuyService;

import jakarta.annotation.Resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GymGroupBuyControllerTest.Config.class)
class GymGroupBuyControllerTest {
    private static final String PATH = "/gym/group-buy/meituan/coupon-history/query";

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(GymGroupBuyController.class)
    static class Config {
        @Bean
        GlobalExceptionHandler handler(ApiErrorLogCommonApi api) {
            return new GlobalExceptionHandler("group-buy-test", api);
        }
    }

    @Resource private GymGroupBuyController controller;
    @Resource private GlobalExceptionHandler handler;
    @MockitoBean private GymGroupBuyService service;
    @MockitoBean private ApiErrorLogCommonApi errorLogApi;

    @MockitoBean(name = "ss")
    private SecurityFrameworkService security;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(security.hasPermission("gym:sales:read")).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(handler).build();
    }

    @Test
    void jsonPostUsesDefaultsAndCommonResult() throws Exception {
        var history = new GymMeituanCouponHistoryRespVO.History();
        history.setTotal(1L);
        var coupon = new GymMeituanCouponHistoryRespVO.Coupon();
        coupon.setCouponCode("001234567890");
        coupon.setIsVoucher(false);
        history.setCouponCodes(List.of(coupon));
        var upstream = new GymMeituanCouponHistoryRespVO();
        upstream.setCode("OP_SUCCESS");
        upstream.setTraceId("test-trace");
        upstream.setData(history);
        when(service.queryMeituanHistory(any())).thenReturn(upstream);
        var response =
                mvc.perform(
                                post(PATH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"storeId\":12,\"date\":\"2026-10-19\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        assertEquals(
                0, JsonUtils.parseObject(response.getContentAsString(), "code", Integer.class));
        var payload =
                JsonUtils.getObjectMapper().readTree(response.getContentAsString()).get("data");
        assertEquals("OP_SUCCESS", payload.get("code").asString());
        assertEquals("test-trace", payload.get("traceId").asString());
        var item = payload.get("data").get("couponCodes").get(0);
        assertEquals("001234567890", item.get("couponCode").asString());
        assertFalse(item.get("isVoucher").asBoolean());
        verify(service)
                .queryMeituanHistory(
                        argThat(
                                req ->
                                        req.getStoreId() == 12L
                                                && req.getOffset() == 0
                                                && req.getLimit() == 20));
    }

    @Test
    void invalidDatePaginationAndStoreAreRejectedBeforeBusinessCall() throws Exception {
        for (String body :
                new String[] {
                    "{\"storeId\":12,\"date\":\"2026-02-30\"}",
                    "{\"storeId\":12,\"date\":\"2026-1-2\"}",
                    "{\"storeId\":12,\"date\":\"2026-10-19\",\"offset\":-1}",
                    "{\"storeId\":12,\"date\":\"2026-10-19\",\"limit\":201}",
                    "{\"storeId\":12,\"date\":\"2026-10-19\",\"limit\":0}",
                    "{\"date\":\"2026-10-19\"}"
                }) {
            var response =
                    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn()
                            .getResponse();
            assertEquals(
                    400,
                    JsonUtils.parseObject(response.getContentAsString(), "code", Integer.class));
        }
        verifyNoInteractions(service);
    }

    @Test
    void missingPermissionPreventsQuery() throws Exception {
        when(security.hasPermission("gym:sales:read")).thenReturn(false);
        var response =
                mvc.perform(
                                post(PATH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"storeId\":12,\"date\":\"2026-10-19\"}"))
                        .andReturn()
                        .getResponse();
        assertEquals(
                403, JsonUtils.parseObject(response.getContentAsString(), "code", Integer.class));
        verifyNoInteractions(service);
    }

    @Test
    void getIsNotSupported() throws Exception {
        var response = mvc.perform(get(PATH)).andReturn().getResponse();
        assertEquals(
                405, JsonUtils.parseObject(response.getContentAsString(), "code", Integer.class));
        verifyNoInteractions(service);
    }
}
