package cn.iocoder.yudao.module.gym.controller.admin.groupbuy;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.STORE_NO_PERMISSION;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailClient;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailContracts;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanResponse;
import cn.iocoder.yudao.module.gym.service.groupbuy.GymMeituanRetailService;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;

import jakarta.annotation.Resource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GymMeituanRetailControllerTest.Config.class)
class GymMeituanRetailControllerTest {
    private static final String ROOT = "/gym/group-buy/meituan/retail/";

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import({GymMeituanRetailController.class, GymMeituanRetailService.class})
    static class Config {
        @Bean
        GlobalExceptionHandler handler(ApiErrorLogCommonApi api) {
            return new GlobalExceptionHandler("retail-test", api);
        }
    }

    @Resource private GymMeituanRetailController controller;
    @Resource private GlobalExceptionHandler handler;
    @MockitoBean private GymMeituanRetailClient client;
    @MockitoBean private GymStoreService stores;
    @MockitoBean private ApiErrorLogCommonApi errorLogApi;

    @MockitoBean(name = "ss")
    private SecurityFrameworkService security;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(security.hasPermission(anyString())).thenReturn(true);
        var user = new LoginUser();
        user.setId(7L);
        user.setTenantId(1L);
        user.setInfo(Map.of(LoginUser.INFO_KEY_NICKNAME, "测试店员"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        TenantContextHolder.setTenantId(1L);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(handler).build();
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource(
            "cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailContracts#cases")
    void allSixteenPostRoutesValidateScopeAndUseTypedPlatformRequests(JsonNode c) throws Exception {
        var mapper = JsonUtils.getObjectMapper();
        var type =
                mapper.getTypeFactory()
                        .constructParametricType(
                                MeituanResponse.class, GymMeituanRetailContracts.responseType(c));
        Object response =
                mapper.readValue(
                        "{\"code\":\"OP_SUCCESS\",\"traceId\":\"mvc-test\",\"data\":"
                                + c.get("data")
                                + "}",
                        type);
        var method = GymMeituanRetailContracts.clientMethod(c);
        var stub = doReturn(response).when(client);
        if (GymMeituanRetailContracts.requestType(c) == null)
            method.invoke(stub, anyLong(), anyLong());
        else
            method.invoke(
                    stub, anyLong(), anyLong(), any(GymMeituanRetailContracts.requestType(c)));
        var local = mapper.createObjectNode().put("storeId", 12L);
        if (!c.get("biz").isNull()) {
            ObjectNode biz = (ObjectNode) mapper.readTree(c.get("biz").toString());
            // 伪造操作人必须被忽略，服务层只能使用当前登录身份。
            if (biz.has("appShopAccount"))
                biz.put("appShopAccount", "spoof").put("appShopAccountName", "spoof");
            if (biz.has("operatorId")) biz.put("operatorId", "spoof");
            if (biz.has("operatorName"))
                biz.put("operatorName", "spoof")
                        .put("operatorIp", "spoof")
                        .put("operatorMobile", "spoof");
            local.set("biz", biz);
        }
        var result = perform(c.get("route").asString(), local.toString());
        assertEquals(0, result.path("code").asInt(), result.toString());
        assertEquals("mvc-test", result.path("data").path("traceId").asString());
        verify(security).hasPermission(c.get("permission").asString());
        verify(stores).requireStore(12L, false);
        var calls =
                mockingDetails(client).getInvocations().stream()
                        .filter(i -> i.getMethod().getName().equals(c.get("method").asString()))
                        .toList();
        assertEquals(1, calls.size());
        var args = calls.getFirst().getArguments();
        assertEquals(1L, args[0]);
        assertEquals(12L, args[1]);
        if (args.length == 3)
            assertEquals(c.get("biz"), mapper.readTree(JsonUtils.toJsonString(args[2])));
    }

    @ParameterizedTest
    @MethodSource(
            "cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailContracts#cases")
    void everyRouteRejectsMissingFunctionPermission(JsonNode c) throws Exception {
        when(security.hasPermission(anyString())).thenReturn(false);
        assertEquals(403, perform(c.get("route").asString(), body(c)).path("code").asInt());
        verifyNoInteractions(stores, client);
    }

    @ParameterizedTest
    @MethodSource(
            "cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailContracts#cases")
    void everyRouteRejectsOutOfScopeStoreBeforeProviderCall(JsonNode c) throws Exception {
        when(stores.requireStore(12L, false)).thenThrow(exception(STORE_NO_PERMISSION));
        assertEquals(
                STORE_NO_PERMISSION.getCode(),
                perform(c.get("route").asString(), body(c)).path("code").asInt());
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidRequestsNeverReachStoreOrProvider(String route, String body) throws Exception {
        assertEquals(400, perform(route, body).path("code").asInt());
        verifyNoInteractions(stores, client);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("receipts/prepare", "{\"storeId\":12,\"biz\":{\"receiptCode\":\"\"}}"),
                Arguments.of("receipts/prepare", "{\"storeId\":12}"),
                Arguments.of("receipts/prepare", "{\"biz\":{\"receiptCode\":\"01\"}}"),
                Arguments.of(
                        "receipts/consume",
                        "{\"storeId\":12,\"biz\":{\"receiptCode\":\"01\",\"count\":101,\"requestId\":\"r\"}}"),
                Arguments.of(
                        "receipts/consume",
                        "{\"storeId\":12,\"biz\":{\"receiptCode\":\"01\",\"count\":1}}"),
                Arguments.of(
                        "receipts/reverse-consume",
                        "{\"storeId\":12,\"biz\":{\"receiptCode\":\"01\"}}"),
                Arguments.of(
                        "receipts/history/query",
                        "{\"storeId\":12,\"biz\":{\"date\":\"2026-02-30\",\"type\":0}}"),
                Arguments.of(
                        "receipts/history/query",
                        "{\"storeId\":12,\"biz\":{\"date\":\"2026-10-19\",\"type\":2}}"),
                Arguments.of(
                        "receipts/history/query",
                        "{\"storeId\":12,\"biz\":{\"date\":\"2026-10-19\",\"type\":0,\"limit\":301}}"),
                Arguments.of("deals/query", "{\"storeId\":12,\"biz\":{\"offset\":0}}"),
                Arguments.of("deals/query", "{\"storeId\":12,\"biz\":{\"limit\":101}}"),
                Arguments.of("products/by-type/query", "{\"storeId\":12,\"biz\":{}}"),
                Arguments.of(
                        "products/by-type/query",
                        "{\"storeId\":12,\"biz\":{\"type\":723,\"pageSize\":101}}"),
                Arguments.of("products/query", "{\"storeId\":12,\"biz\":{\"pageNo\":0}}"),
                Arguments.of(
                        "receipts/by-mobile/query",
                        "{\"storeId\":12,\"biz\":{\"mobile\":\"bad\",\"dealGroupId\":1,\"dealId\":1,\"platform\":2}}"),
                Arguments.of(
                        "receipts/by-mobile/query",
                        "{\"storeId\":12,\"biz\":{\"mobile\":\"13866666666\",\"dealGroupId\":1,\"dealId\":1,\"platform\":2,\"limit\":31}}"),
                Arguments.of(
                        "receipts/batch-consume",
                        "{\"storeId\":12,\"biz\":{\"requestId\":\"r\",\"receiptCodeInfos\":[]}}"),
                Arguments.of(
                        "receipts/batch-consume",
                        "{\"storeId\":12,\"biz\":{\"requestId\":\"r\",\"receiptCodeInfos\":[{\"receiptCode\":\"01\",\"count\":\"101\"}]}}"),
                Arguments.of("refunds/precheck", "{\"storeId\":12,\"biz\":{}}"),
                Arguments.of("refunds/apply", "{\"storeId\":12,\"biz\":{\"orderId\":\"o\"}}"),
                Arguments.of(
                        "refunds/apply",
                        "{\"storeId\":12,\"biz\":{\"orderId\":\"o\",\"sequenceId\":\"s\",\"refundFeeType\":true}}"),
                Arguments.of(
                        "refunds/query",
                        "{\"storeId\":12,\"biz\":{\"orderId\":\"o\",\"sequenceId\":\"s\"}}"),
                Arguments.of(
                        "refunds/audit",
                        "{\"storeId\":12,\"biz\":{\"orderId\":\"o\",\"refundId\":\"r\",\"sequenceId\":\"s\",\"auditResult\":3,\"refundFeeType\":false,\"refundAmount\":1}}"),
                Arguments.of(
                        "receipts/batch-reverse-consume",
                        "{\"storeId\":12,\"biz\":{\"receiptCodeList\":[],\"dealId\":\"1\"}}"),
                Arguments.of("standard-products/query", "{\"storeId\":0}"));
    }

    @Test
    void missingTrustedIdentityBlocksStateChangingRequest() throws Exception {
        SecurityContextHolder.clearContext();
        assertEquals(
                401,
                perform(
                                "receipts/consume",
                                "{\"storeId\":12,\"biz\":{\"receiptCode\":\"01\",\"count\":1,\"requestId\":\"r\"}}")
                        .path("code")
                        .asInt());
        verifyNoInteractions(client);
    }

    private String body(JsonNode c) {
        return c.get("biz").isNull()
                ? "{\"storeId\":12}"
                : "{\"storeId\":12,\"biz\":" + c.get("biz") + "}";
    }

    private JsonNode perform(String route, String body) throws Exception {
        var response =
                mvc.perform(
                                post(ROOT + route)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andReturn()
                        .getResponse();
        return JsonUtils.getObjectMapper().readTree(response.getContentAsString());
    }
}
