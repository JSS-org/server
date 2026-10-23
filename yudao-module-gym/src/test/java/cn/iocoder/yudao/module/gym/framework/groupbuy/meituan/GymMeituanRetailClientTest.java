package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanResponse;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanReceiptCodeRequest;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

class GymMeituanRetailClientTest {
    private GymMeituanProperties properties;
    private GymMeituanTransport transport;
    private GymMeituanRetailClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        properties = new GymMeituanProperties();
        properties.setEnabled(true);
        properties.setMutationsEnabled(true);
        properties.setDeveloperId(10010L);
        properties.setSignKey("test-key");
        var auth = new GymMeituanProperties.StoreAuthorization();
        auth.setAppAuthToken("test-token+&=中文");
        auth.setRealOpBizCode("store+&=中文");
        var tenant = new GymMeituanProperties.TenantAuthorization();
        tenant.getStores().put(12L, auth);
        properties.getTenants().put(1L, tenant);
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        transport = new GymMeituanTransport(properties, builder.build());
        client = new GymMeituanRetailClient();
        ReflectionTestUtils.setField(client, "transport", transport);
    }

    @ParameterizedTest
    @MethodSource(
            "cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailContracts#cases")
    void allSixteenContractsSendTypedSignedPostsAndDecodeTypedResults(JsonNode c) throws Exception {
        server.expect(requestTo("https://api-open-cater.meituan.com" + c.get("path").asString()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(
                        request -> {
                            var raw = ((MockClientHttpRequest) request).getBodyAsString();
                            var params = decode(raw);
                            assertEquals("58", params.get("businessId"));
                            assertEquals("test-token+&=中文", params.get("appAuthToken"));
                            assertEquals("store+&=中文", params.get("realOpBizCode"));
                            assertEquals("10010", params.get("developerId"));
                            assertEquals("2", params.get("version"));
                            assertEquals("utf-8", params.get("charset"));
                            assertTrue(params.get("timestamp").matches("\\d{10}"));
                            assertEquals(
                                    GymMeituanTransport.sign("test-key", params),
                                    params.get("sign"));
                            assertFalse(raw.contains("中文"));
                            if (c.get("biz").isNull()) assertFalse(params.containsKey("biz"));
                            else
                                assertEquals(
                                        c.get("biz"),
                                        JsonUtils.getObjectMapper().readTree(params.get("biz")));
                            assertFalse(params.containsKey("storeId"));
                            assertFalse(params.containsKey("tenantId"));
                            assertFalse(params.containsKey("signKey"));
                        })
                .andRespond(
                        withSuccess(
                                "{\"code\":\"OP_SUCCESS\",\"traceId\":\"test-trace\",\"data\":"
                                        + c.get("data")
                                        + "}",
                                MediaType.APPLICATION_JSON));
        var response = (MeituanResponse<?>) GymMeituanRetailContracts.invoke(client, c);
        assertInstanceOf(GymMeituanRetailContracts.responseType(c), response.getData());
        assertEquals("test-trace", response.getTraceId());
        var data = JsonUtils.getObjectMapper().readTree(JsonUtils.toJsonString(response.getData()));
        switch (c.get("method").asString()) {
            case "prepare" -> {
                var result = (Prepare) response.getData();
                assertEquals("0012345678901", result.getReceiptCode());
                assertEquals(9007199254740993L, result.getDealId());
                assertEquals(0, new BigDecimal("12.30").compareTo(result.getDealPrice()));
            }
            case "consume" ->
                    assertEquals(
                            "900719925474099312345",
                            ((Consume) response.getData()).getResult().getFirst().getFlowId());
            case "batchConsume" ->
                    assertEquals(
                            1,
                            ((BatchConsume) response.getData())
                                    .getResult()
                                    .getFirst()
                                    .getSingleConsumeCode());
            case "refundPrecheck" ->
                    assertInstanceOf(
                            RefundReceipt.class,
                            ((RefundPrecheck) response.getData()).getResult().getReceiptList());
            case "refundQuery" ->
                    assertEquals(
                            1,
                            ((RefundQuery) response.getData()).getResult().getReceiptList().size());
            default -> assertTrue(data.isObject());
        }
        server.verify();
    }

    @Test
    void retailAndCateringAuthorizationCannotBeMixed() {
        properties.getTenants().get(1L).getStores().get(12L).setBusinessId(1);
        assertCode(
                GROUP_BUY_PROVIDER_SCENE_MISMATCH.getCode(),
                () -> client.prepare(1L, 12L, codeRequest()));
        properties.getTenants().get(1L).getStores().get(12L).setBusinessId(58);
        assertCode(
                GROUP_BUY_PROVIDER_SCENE_MISMATCH.getCode(),
                () ->
                        transport.post(
                                1L,
                                12L,
                                MeituanApi.CATERING_HISTORY,
                                codeRequest(),
                                Prepare.class));
        server.verify();
    }

    @Test
    void missingTenantStoreAndDisabledConfigurationNeverSend() {
        assertCode(
                GROUP_BUY_PROVIDER_UNCONFIGURED.getCode(),
                () -> client.prepare(2L, 12L, codeRequest()));
        assertCode(
                GROUP_BUY_PROVIDER_UNCONFIGURED.getCode(),
                () -> client.prepare(1L, 99L, codeRequest()));
        properties.setEnabled(false);
        assertCode(
                GROUP_BUY_PROVIDER_UNCONFIGURED.getCode(),
                () -> client.prepare(1L, 12L, codeRequest()));
        server.verify();
    }

    @Test
    void everyStateChangingOperationRequiresSeparateEnablement() {
        properties.setMutationsEnabled(false);
        for (var api : MeituanApi.values())
            if (api.isMutation())
                assertCode(
                        GROUP_BUY_PROVIDER_MUTATIONS_DISABLED.getCode(),
                        () -> transport.post(1L, 12L, api, codeRequest(), Consume.class));
        server.verify();
    }

    @Test
    void everyMutationTimeoutReturnsUnknownWithoutRetryOrLeakingTransportDetails() {
        for (var api : MeituanApi.values())
            if (api.isMutation()) {
                server.reset();
                server.expect(requestTo("https://api-open-cater.meituan.com" + api.getPath()))
                        .andRespond(withException(new SocketTimeoutException("test-only secret")));
                var error =
                        assertThrows(
                                ServiceException.class,
                                () -> transport.post(1L, 12L, api, codeRequest(), Consume.class));
                assertEquals(GROUP_BUY_PROVIDER_RESULT_UNKNOWN.getCode(), error.getCode());
                assertFalse(error.getMessage().contains("secret"));
                server.verify();
            }
    }

    @Test
    void malformedMutationResponseIsUnknownWhileExplicitRejectionStaysRejected() {
        server.expect(requestTo("https://api-open-cater.meituan.com/ddzh/tuangou/receipt/consume"))
                .andRespond(withSuccess("<html>proxy error</html>", MediaType.TEXT_HTML));
        assertCode(
                GROUP_BUY_PROVIDER_RESULT_UNKNOWN.getCode(),
                () -> transport.post(1L, 12L, MeituanApi.CONSUME, codeRequest(), Consume.class));
        server.verify();
        server.reset();
        server.expect(requestTo("https://api-open-cater.meituan.com/ddzh/tuangou/receipt/consume"))
                .andRespond(
                        withSuccess(
                                "{\"code\":\"OP_SYSTEM_PARAM_ERROR\",\"msg\":\"test-only secret\"}",
                                MediaType.APPLICATION_JSON));
        assertCode(
                GROUP_BUY_PROVIDER_REJECTED.getCode(),
                () -> transport.post(1L, 12L, MeituanApi.CONSUME, codeRequest(), Consume.class));
        server.verify();
    }

    @Test
    void partialExecutionBusinessErrorRequiresConfirmationInsteadOfReportingFullRejection() {
        server.expect(requestTo("https://api-open-cater.meituan.com/ddzh/tuangou/receipt/consume"))
                .andRespond(
                        withSuccess(
                                "{\"code\":\"1004\",\"msg\":\"部分验证成功\"}",
                                MediaType.APPLICATION_JSON));
        assertCode(
                GROUP_BUY_PROVIDER_RESULT_UNKNOWN.getCode(),
                () -> transport.post(1L, 12L, MeituanApi.CONSUME, codeRequest(), Consume.class));
        server.verify();
    }

    @Test
    void queryCannotAcceptSuccessEnvelopeMissingRequiredPayload() {
        server.expect(requestTo("https://api-open-cater.meituan.com/ddzh/tuangou/receipt/prepare"))
                .andRespond(
                        withSuccess(
                                "{\"code\":\"OP_SUCCESS\",\"data\":{}}",
                                MediaType.APPLICATION_JSON));
        assertCode(
                GROUP_BUY_PROVIDER_RESPONSE_INVALID.getCode(),
                () -> client.prepare(1L, 12L, codeRequest()));
        server.verify();
    }

    private MeituanReceiptCodeRequest codeRequest() {
        var req = new MeituanReceiptCodeRequest();
        req.setReceiptCode("0012345678901");
        return req;
    }

    private void assertCode(int expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(ServiceException.class, action).getCode());
    }

    private Map<String, String> decode(String body) {
        var result = new HashMap<String, String>();
        for (var part : body.split("&")) {
            var pair = part.split("=", 2);
            result.put(
                    URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
