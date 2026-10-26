package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileCouponProperties;
import cn.iocoder.yudao.module.gym.service.groupbuy.GymCouponCipher;
import cn.iocoder.yudao.module.gym.service.groupbuy.GymMemberCouponConsumption;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

class GymMemberCouponConsumptionTest {
    private GymMeituanProperties provider;
    private GymMeituanProperties.StoreAuthorization authorization;
    private GymMobileCouponProperties mobile;
    private GymMobileCouponProperties.MerchantAccount merchant;
    private GymCouponCipher cipher;
    private GymMemberCouponConsumption service;
    private MockRestServiceServer server;
    private GymMemberCouponConsumption.Scope scope;
    private static final String CODE = "0012345678901";
    private static final String REQUEST = "gym-coupon-14fce9f1-2b83-4980-9503-253b8b813939";

    @BeforeEach
    void setup() {
        provider = new GymMeituanProperties();
        provider.setEnabled(true);
        provider.setMutationsEnabled(true);
        provider.setDeveloperId(10010L);
        provider.setSignKey("test-key");
        authorization = new GymMeituanProperties.StoreAuthorization();
        authorization.setAppAuthToken("test-token+&=中文");
        authorization.setRealOpBizCode("store-12");
        var tenant = new GymMeituanProperties.TenantAuthorization();
        tenant.getStores().put(12L, authorization);
        provider.getTenants().put(1L, tenant);
        mobile = new GymMobileCouponProperties();
        mobile.setEnabled(true);
        mobile.setConsumptionEnabled(true);
        mobile.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        merchant = new GymMobileCouponProperties.MerchantAccount();
        merchant.setAccount("merchant-audit-account");
        merchant.setName("门店自助核销");
        mobile.getMerchantAccounts().put(1L, new HashMap<>(Map.of(12L, merchant)));
        cipher = new GymCouponCipher();
        ReflectionTestUtils.setField(cipher, "properties", mobile);
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var transport = new GymMeituanTransport(provider, builder.build());
        var client = new GymMeituanRetailClient();
        ReflectionTestUtils.setField(client, "transport", transport);
        service = new GymMemberCouponConsumption();
        ReflectionTestUtils.setField(service, "meituan", client);
        ReflectionTestUtils.setField(service, "provider", provider);
        ReflectionTestUtils.setField(service, "mobile", mobile);
        ReflectionTestUtils.setField(service, "cipher", cipher);
        scope = service.requireConsumptionScope(1L, 12L);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    private Map<String, Object> receipt() {
        var row = new LinkedHashMap<String, Object>();
        row.put("receiptCode", CODE);
        row.put("dealId", 7L);
        row.put("dealGroupId", 8L);
        row.put("bizType", 0);
        row.put("tgTimesCardFlag", false);
        row.put("flowId", "flow-001");
        row.put("orderId", "order-002");
        row.put("mobile", "13800000001");
        return row;
    }

    private String response(Object data) {
        return JsonUtils.toJsonString(
                Map.of("code", "OP_SUCCESS", "traceId", "trace-003", "data", data));
    }

    private void consumeResponse(Object data) {
        server.expect(requestTo(GymMeituanTransport.BASE_URL + MeituanApi.CONSUME.getPath()))
                .andRespond(withSuccess(response(data), MediaType.APPLICATION_JSON));
    }

    private GymMemberCouponConsumption.Confirmation consume() {
        return service.consume(1L, 12L, CODE, 7L, 8L, REQUEST, scope);
    }

    @Test
    void sendsExactlyOneSignedSingleVoucherRequestWithFrozenMerchantAndRequestId() {
        server.expect(requestTo(GymMeituanTransport.BASE_URL + MeituanApi.CONSUME.getPath()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        request -> {
                            Map<String, String> params = new HashMap<>();
                            for (String pair :
                                    ((MockClientHttpRequest) request)
                                            .getBodyAsString()
                                            .split("&")) {
                                var item = pair.split("=", 2);
                                params.put(
                                        URLDecoder.decode(item[0], StandardCharsets.UTF_8),
                                        URLDecoder.decode(item[1], StandardCharsets.UTF_8));
                            }
                            assertEquals(
                                    GymMeituanTransport.sign("test-key", params),
                                    params.get("sign"));
                            assertEquals("58", params.get("businessId"));
                            assertEquals("test-token+&=中文", params.get("appAuthToken"));
                            assertEquals("store-12", params.get("realOpBizCode"));
                            var biz = JsonUtils.getObjectMapper().readTree(params.get("biz"));
                            assertEquals(CODE, biz.get("receiptCode").asString());
                            assertEquals(REQUEST, biz.get("requestId").asString());
                            assertEquals(1, biz.get("count").asInt());
                            assertEquals(
                                    "merchant-audit-account", biz.get("appShopAccount").asString());
                            assertEquals("门店自助核销", biz.get("appShopAccountName").asString());
                            assertFalse(biz.has("memberId"));
                            assertFalse(biz.has("mobile"));
                        })
                .andRespond(
                        withSuccess(
                                response(Map.of("result", List.of(receipt()))),
                                MediaType.APPLICATION_JSON));
        var result = consume();
        assertEquals(REQUEST, result.requestId());
        assertEquals("flow-001", result.flowId());
        assertEquals("order-002", result.orderId());
        assertEquals("trace-003", result.traceId());
        assertEquals(7L, result.dealId());
        assertEquals(8L, result.dealGroupId());
        assertEquals(cipher.hash(1L, "meituan", CODE), result.couponHash());
        assertEquals(scope.fingerprint(), result.authorizationFingerprint());
        String json = JsonUtils.toJsonString(result);
        for (String secret :
                List.of(CODE, "13800000001", "test-key", "test-token", "merchant-audit-account"))
            assertFalse(json.contains(secret));
        assertFalse(scope.toString().contains(merchant.getAccount()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "empty",
                "duplicate",
                "nullRow",
                "wrongCode",
                "wrongDeal",
                "wrongGroup",
                "missingBiz",
                "wrongBiz",
                "timesCard",
                "missingTimesCard",
                "quantity",
                "missingFlow",
                "missingOrder",
                "blankFlow",
                "controlFlow",
                "longOrder",
                "related",
                "missingResult"
            })
    void successfulEnvelopeWithoutExactSingleVoucherProofRemainsUnknown(String kind) {
        var row = receipt();
        switch (kind) {
            case "wrongCode" -> row.put("receiptCode", "112345678901");
            case "wrongDeal" -> row.put("dealId", 9L);
            case "wrongGroup" -> row.put("dealGroupId", 9L);
            case "missingBiz" -> row.remove("bizType");
            case "wrongBiz" -> row.put("bizType", 1);
            case "timesCard" -> row.put("tgTimesCardFlag", true);
            case "missingTimesCard" -> row.remove("tgTimesCardFlag");
            case "quantity" -> row.put("dealQuantity", 2);
            case "missingFlow" -> row.remove("flowId");
            case "missingOrder" -> row.remove("orderId");
            case "blankFlow" -> row.put("flowId", " ");
            case "controlFlow" -> row.put("flowId", "flow\n001");
            case "longOrder" -> row.put("orderId", "o".repeat(129));
        }
        Map<String, Object> data = new HashMap<>();
        data.put(
                "result",
                switch (kind) {
                    case "empty" -> List.of();
                    case "duplicate" -> List.of(row, row);
                    case "nullRow" -> Collections.singletonList(null);
                    default -> List.of(row);
                });
        if (kind.equals("related"))
            data.put("relatedReceiptInfo", List.of(Map.of("receiptCode", "other")));
        if (kind.equals("missingResult")) data.remove("result");
        consumeResponse(data);
        assertServiceException(this::consume, GROUP_BUY_PROVIDER_RESULT_UNKNOWN);
    }

    @Test
    void timeoutHasNoMutationRetryOrImplicitObservation() {
        server.expect(requestTo(GymMeituanTransport.BASE_URL + MeituanApi.CONSUME.getPath()))
                .andRespond(withException(new SocketTimeoutException("private " + CODE)));
        var failure = assertThrows(ServiceException.class, this::consume);
        assertEquals(GROUP_BUY_PROVIDER_RESULT_UNKNOWN.getCode(), failure.getCode());
        assertFalse(failure.getMessage().contains(CODE));
        assertNull(failure.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OP_SYSTEM_PARAM_ERROR", "OP_BIZ_ERROR", "not-json", "null-data"})
    void preservesOnlyExplicitParameterRejectionAsDefinitive(String kind) {
        String body =
                switch (kind) {
                    case "not-json" -> "private " + CODE;
                    case "null-data" -> "{\"code\":\"OP_SUCCESS\",\"data\":null}";
                    default ->
                            JsonUtils.toJsonString(Map.of("code", kind, "msg", "private " + CODE));
                };
        server.expect(requestTo(GymMeituanTransport.BASE_URL + MeituanApi.CONSUME.getPath()))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertServiceException(
                this::consume,
                kind.equals("OP_SYSTEM_PARAM_ERROR")
                        ? GROUP_BUY_PROVIDER_REJECTED
                        : GROUP_BUY_PROVIDER_RESULT_UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "token",
                "key",
                "developer",
                "realOpBiz",
                "merchantAccount",
                "merchantName",
                "encryptionKey"
            })
    void authorizationChangeRejectsStaleAttemptBeforeNetwork(String kind) {
        switch (kind) {
            case "token" -> authorization.setAppAuthToken("new-token");
            case "key" -> provider.setSignKey("new-sign-key");
            case "developer" -> provider.setDeveloperId(10011L);
            case "realOpBiz" -> authorization.setRealOpBizCode("other-shop");
            case "merchantAccount" -> merchant.setAccount("other-account");
            case "merchantName" -> merchant.setName("other-name");
            case "encryptionKey" ->
                    mobile.setEncryptionKey(
                            Base64.getEncoder()
                                    .encodeToString(
                                            "12345678901234567890123456789012"
                                                    .getBytes(StandardCharsets.UTF_8)));
        }
        assertNotEquals(
                scope.fingerprint(), service.requireConsumptionScope(1L, 12L).fingerprint());
        assertServiceException(this::consume, COUPON_PREVIEW_INVALID);
        assertServiceException(
                () -> service.observe(1L, 12L, CODE, 7L, 8L, scope), COUPON_PREVIEW_INVALID);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "disabled",
                "consumptionDisabled",
                "missingMerchant",
                "nullMerchantMap",
                "blankAccount",
                "controlName",
                "longName",
                "noEncryptionKey"
            })
    void previewAndProviderEnablementDoNotAuthorizeUnconfiguredMemberConsumption(String kind) {
        switch (kind) {
            case "disabled" -> mobile.setEnabled(false);
            case "consumptionDisabled" -> mobile.setConsumptionEnabled(false);
            case "missingMerchant" -> mobile.getMerchantAccounts().clear();
            case "nullMerchantMap" -> mobile.setMerchantAccounts(null);
            case "blankAccount" -> merchant.setAccount(" ");
            case "controlName" -> merchant.setName("name\nprivate");
            case "longName" -> merchant.setName("n".repeat(129));
            case "noEncryptionKey" -> mobile.setEncryptionKey(null);
        }
        assertServiceException(this::consume, COUPON_NOT_CONFIGURED);
    }

    @Test
    void providerMutationGateStillApplies() {
        provider.setMutationsEnabled(false);
        assertServiceException(this::consume, GROUP_BUY_PROVIDER_MUTATIONS_DISABLED);
    }

    @Test
    void noTenantOrStoreCredentialFallbackAndWrongIndustryCannotDispatch() {
        assertServiceException(
                () -> service.requireConsumptionScope(2L, 12L), GROUP_BUY_PROVIDER_UNCONFIGURED);
        assertServiceException(
                () -> service.requireConsumptionScope(1L, 13L), GROUP_BUY_PROVIDER_UNCONFIGURED);
        authorization.setBusinessId(1);
        assertServiceException(this::consume, GROUP_BUY_PROVIDER_SCENE_MISMATCH);
        provider.setTenants(null);
        assertServiceException(this::consume, GROUP_BUY_PROVIDER_UNCONFIGURED);
    }

    @Test
    void rejectsInvalidOrMissingFrozenInputBeforeDispatch() {
        assertServiceException(
                () -> service.consume(1L, 12L, CODE, 7L, 8L, "", scope), COUPON_PREVIEW_INVALID);
        assertServiceException(
                () -> service.consume(1L, 12L, CODE, 7L, 8L, "a\nb", scope),
                COUPON_PREVIEW_INVALID);
        assertServiceException(
                () -> service.consume(1L, 12L, CODE, 7L, 8L, REQUEST, null),
                COUPON_PREVIEW_INVALID);
        assertServiceException(
                () -> service.consume(1L, 12L, " ", 7L, 8L, REQUEST, scope),
                COUPON_PREVIEW_INVALID);
        assertServiceException(
                () -> service.consume(1L, 12L, CODE, 0L, 8L, REQUEST, scope),
                COUPON_PREVIEW_INVALID);
        assertServiceException(
                () -> service.consume(1L, 12L, CODE, 7L, null, REQUEST, scope),
                COUPON_PREVIEW_INVALID);
    }

    @Test
    void readOnlyObservationWorksWithConsumptionDisabledButCannotBeConfirmation() {
        mobile.setEnabled(false);
        mobile.setConsumptionEnabled(false);
        provider.setMutationsEnabled(false);
        var row = receipt();
        row.remove("flowId");
        row.remove("tgTimesCardFlag");
        row.put("verifyAccount", "another-operator");
        row.put("verifyTime", "2026-10-01 10:00:00");
        server.expect(requestTo(GymMeituanTransport.BASE_URL + MeituanApi.GET_CONSUMED.getPath()))
                .andRespond(withSuccess(response(row), MediaType.APPLICATION_JSON));
        var observation = service.observe(1L, 12L, CODE, 7L, 8L, scope);
        assertEquals("order-002", observation.orderId());
        assertEquals(cipher.hash(1L, "meituan", CODE), observation.couponHash());
        var json = JsonUtils.getObjectMapper().readTree(JsonUtils.toJsonString(observation));
        assertEquals(5, json.size());
        for (String field :
                List.of(
                        "flowId",
                        "requestId",
                        "status",
                        "memberCardIds",
                        "receiptCode",
                        "verifyAccount",
                        "mobile")) assertFalse(json.has(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrongCode", "wrongDeal", "wrongGroup", "wrongBiz", "missingOrder"})
    void observationRejectsUnrelatedOrIncompleteReceipt(String kind) {
        var row = receipt();
        switch (kind) {
            case "wrongCode" -> row.put("receiptCode", "other");
            case "wrongDeal" -> row.put("dealId", 9L);
            case "wrongGroup" -> row.put("dealGroupId", 9L);
            case "wrongBiz" -> row.put("bizType", 1);
            case "missingOrder" -> row.remove("orderId");
        }
        server.expect(requestTo(GymMeituanTransport.BASE_URL + MeituanApi.GET_CONSUMED.getPath()))
                .andRespond(withSuccess(response(row), MediaType.APPLICATION_JSON));
        assertServiceException(
                () -> service.observe(1L, 12L, CODE, 7L, 8L, scope), COUPON_PROVIDER_INVALID);
    }

    @Test
    void transactionProxyPreventsNetworkInsideCallerTransaction() {
        var manager =
                new DataSourceTransactionManager(
                        new DriverManagerDataSource("jdbc:h2:mem:coupon_network_boundary"));
        var proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(
                new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var proxy = (GymMemberCouponConsumption) proxyFactory.getProxy();
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            assertThrows(
                                    IllegalTransactionStateException.class,
                                    () -> proxy.consume(1L, 12L, CODE, 7L, 8L, REQUEST, scope));
                            assertThrows(
                                    IllegalTransactionStateException.class,
                                    () -> proxy.observe(1L, 12L, CODE, 7L, 8L, scope));
                        });
    }
}
