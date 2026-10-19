package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanCouponHistoryRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class GymMeituanCouponClientTest {
    private GymMeituanProperties properties;
    private GymMeituanCouponClient client;
    private MockRestServiceServer server;
    private MeituanCouponHistoryRequest req;

    @BeforeEach
    void setup() {
        properties = new GymMeituanProperties();
        properties.setEnabled(true);
        properties.setDeveloperId(10010L);
        properties.setSignKey("test-key");
        var auth = new GymMeituanProperties.StoreAuthorization();
        auth.setAppAuthToken("test-token+&=中文");
        var tenant = new GymMeituanProperties.TenantAuthorization();
        tenant.getStores().put(12L, auth);
        properties.getTenants().put(1L, tenant);
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GymMeituanCouponClient(properties, builder.build());
        req = new MeituanCouponHistoryRequest();
        req.setOffset(0);
        req.setLimit(20);
        req.setDate("2026-10-19");
    }

    @Test
    void signatureMatchesOfficialExampleAndIgnoresSignAndEmptyValues() {
        var params = new HashMap<String, String>();
        params.put("timestamp", "1577771730");
        params.put("developerId", "123123");
        params.put("biz", "{\"ticket\":\"10346762252\",\"number\":2}");
        params.put("version", "2");
        params.put("charset", "UTF-8");
        params.put(
                "appAuthToken",
                "74d301d082f366b730043dc0a55b403ac0f7ac9e63c93cedd3ab9759a06a1bbf3fc953de835c");
        params.put("SIGN", "ignored");
        params.put("empty", "");
        params.put("null", null);
        assertEquals(
                "ceb760af62431e20fd2e4aa1bbf21d2c17012dc0",
                GymMeituanCouponClient.sign("123", params));
    }

    @Test
    void sendsSignedFormPostAndPreservesCouponFields() {
        server.expect(requestTo(GymMeituanCouponClient.HISTORY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(
                        request -> {
                            var raw = ((MockClientHttpRequest) request).getBodyAsString();
                            assertFalse(raw.contains("中文"));
                            var form = decode(raw);
                            assertEquals(
                                    Set.of(
                                            "appAuthToken",
                                            "businessId",
                                            "charset",
                                            "developerId",
                                            "timestamp",
                                            "version",
                                            "biz",
                                            "sign"),
                                    form.keySet());
                            assertEquals("test-token+&=中文", form.get("appAuthToken"));
                            assertEquals("1", form.get("businessId"));
                            assertEquals("10010", form.get("developerId"));
                            assertEquals("2", form.get("version"));
                            assertEquals("utf-8", form.get("charset"));
                            assertTrue(form.get("timestamp").matches("\\d{10}"));
                            var biz = JsonUtils.getObjectMapper().readTree(form.get("biz"));
                            assertEquals("2026-10-19", biz.get("date").asString());
                            assertEquals(0, biz.get("offset").asInt());
                            assertEquals(20, biz.get("limit").asInt());
                            assertEquals(3, biz.size());
                            assertEquals(
                                    GymMeituanCouponClient.sign("test-key", form),
                                    form.get("sign"));
                            assertFalse(form.containsKey("storeId"));
                            assertFalse(form.containsKey("tenantId"));
                        })
                .andRespond(
                        withSuccess(
                                """
                    {"code":"OP_SUCCESS","msg":"成功","traceId":"trace-test","data":{
                    "total":1,"couponCodes":[{"couponCode":"001234567890","couponBuyPrice":12.30,
                    "dealValue":20.00,"couponStatusDesc":"已使用","isVoucher":false,
                    "couponUseTime":"2026-10-19 12:00:00","sqtInfo":{"sqtOrder":1,"sqtNeedInvoice":0}}]}}
                    """,
                                MediaType.APPLICATION_JSON));
        var result = client.queryHistory(1L, 12L, req);
        assertEquals("trace-test", result.getTraceId());
        assertEquals(1L, result.getData().getTotal());
        var coupon = result.getData().getCouponCodes().getFirst();
        assertEquals("001234567890", coupon.getCouponCode());
        assertEquals(new BigDecimal("12.30"), coupon.getCouponBuyPrice());
        assertEquals(1, coupon.getSqtInfo().getSqtOrder());
        server.verify();
    }

    @Test
    void sendsOptionalAuthorizationEntityInBothSignatureAndForm() {
        properties.getTenants().get(1L).getStores().get(12L).setRealOpBizCode("shop+&=中文");
        server.expect(requestTo(GymMeituanCouponClient.HISTORY_URL))
                .andExpect(
                        request -> {
                            var form = decode(((MockClientHttpRequest) request).getBodyAsString());
                            assertEquals("shop+&=中文", form.get("realOpBizCode"));
                            assertEquals(
                                    GymMeituanCouponClient.sign("test-key", form),
                                    form.get("sign"));
                            var withoutEntity = new HashMap<>(form);
                            withoutEntity.remove("realOpBizCode");
                            assertNotEquals(
                                    GymMeituanCouponClient.sign("test-key", withoutEntity),
                                    form.get("sign"));
                        })
                .andRespond(
                        withSuccess(
                                "{\"code\":\"OP_SUCCESS\",\"data\":{\"total\":0,\"couponCodes\":[]}}",
                                MediaType.APPLICATION_JSON));
        var result = client.queryHistory(1L, 12L, req);
        assertEquals(0L, result.getData().getTotal());
        assertTrue(result.getData().getCouponCodes().isEmpty());
        server.verify();
    }

    @Test
    void missingTenantOrStoreAuthorizationAndDisabledProviderNeverSendRequest() {
        assertError(
                GROUP_BUY_PROVIDER_UNCONFIGURED.getCode(), () -> client.queryHistory(2L, 12L, req));
        assertError(
                GROUP_BUY_PROVIDER_UNCONFIGURED.getCode(), () -> client.queryHistory(1L, 13L, req));
        req.setOffset(0);
        req.setLimit(20);
        properties.setEnabled(false);
        assertError(
                GROUP_BUY_PROVIDER_UNCONFIGURED.getCode(), () -> client.queryHistory(1L, 12L, req));
        server.verify();
    }

    @Test
    void upstreamBusinessErrorDoesNotBecomeSuccessfulEmptyHistoryOrLeakBody() {
        server.expect(requestTo(GymMeituanCouponClient.HISTORY_URL))
                .andRespond(
                        withSuccess(
                                "{\"code\":\"OP_SYSTEM_PARAM_ERROR\",\"msg\":\"test-secret\",\"traceId\":\"upstream\"}",
                                MediaType.APPLICATION_JSON));
        var error = assertThrows(ServiceException.class, () -> client.queryHistory(1L, 12L, req));
        assertEquals(GROUP_BUY_PROVIDER_REJECTED.getCode(), error.getCode());
        assertFalse(error.getMessage().contains("test-secret"));
        server.verify();
    }

    @Test
    void nonJsonResponseFailsExplicitly() {
        server.expect(requestTo(GymMeituanCouponClient.HISTORY_URL))
                .andRespond(withSuccess("<html>unavailable</html>", MediaType.TEXT_HTML));
        assertError(
                GROUP_BUY_PROVIDER_RESPONSE_INVALID.getCode(),
                () -> client.queryHistory(1L, 12L, req));
        server.verify();
    }

    @Test
    void successWithoutHistoryPayloadIsRejected() {
        server.expect(requestTo(GymMeituanCouponClient.HISTORY_URL))
                .andRespond(withSuccess("{\"code\":\"OP_SUCCESS\"}", MediaType.APPLICATION_JSON));
        assertError(
                GROUP_BUY_PROVIDER_RESPONSE_INVALID.getCode(),
                () -> client.queryHistory(1L, 12L, req));
        server.verify();
    }

    @Test
    void networkTimeoutReturnsStableErrorWithoutRetryOrTransportDetails() {
        server.expect(requestTo(GymMeituanCouponClient.HISTORY_URL))
                .andRespond(
                        withException(new SocketTimeoutException("test-only transport details")));
        var error = assertThrows(ServiceException.class, () -> client.queryHistory(1L, 12L, req));
        assertEquals(GROUP_BUY_PROVIDER_REQUEST_FAILED.getCode(), error.getCode());
        assertFalse(error.getMessage().contains("transport details"));
        server.verify();
    }

    @Test
    void httpFailureDoesNotRetryOrReturnFakeSuccess() {
        server.expect(requestTo(GymMeituanCouponClient.HISTORY_URL)).andRespond(withServerError());
        assertError(
                GROUP_BUY_PROVIDER_REQUEST_FAILED.getCode(),
                () -> client.queryHistory(1L, 12L, req));
        server.verify();
    }

    private static void assertError(
            int expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(ServiceException.class, action).getCode());
    }

    private static Map<String, String> decode(String encoded) {
        var result = new HashMap<String, String>();
        for (var item : encoded.split("&")) {
            var pair = item.split("=", 2);
            result.put(
                    URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
