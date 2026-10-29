package cn.iocoder.yudao.server.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.server.YudaoServerApplication;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.sql.DataSource;

/** Real catalog, cart, wallet payment, fulfillment and refunds on disposable MySQL and owned Redis. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_HTTP_SCHEMA",
        matches = "jss_http_verify_[0-9a-f]{12}")
@ContextConfiguration(initializers = JssHttpFixtures.class)
@SpringBootTest(
        classes = YudaoServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=local",
            "server.address=127.0.0.1",
            "spring.quartz.auto-startup=false",
            "yudao.iot.storage.type=disabled",
            "spring.boot.admin.client.enabled=false",
            "xxl.job.enabled=false",
            "yudao.captcha.enable=false",
            "yudao.security.mock-enable=false",
            "jss.workspace.enabled=true",
            "yudao.trade.order.status-sync-to-wxa-enable=false",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=16379",
            "spring.data.redis.database=14",
            "spring.data.redis.password=${JSS_TEST_REDIS_PASSWORD}",
            "logging.file.name=${JSS_TEST_RUNTIME_DIR}/server.log",
            "logging.file.path=${JSS_TEST_RUNTIME_DIR}",
            "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
        })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JssMallHttpIT {
    @Autowired private Environment environment;
    @Autowired private DataSource datasource;
    @Autowired private StringRedisTemplate redis;
    @Autowired private SecurityProperties security;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<String> memberTokens = new ArrayList<>();
    private String admin;
    private String buyer;
    private String other;
    private long address;
    private long otherAddress;
    private long spu;
    private long sku;
    private long carrier;
    private String name;
    private final String image =
            "1".equals(System.getenv("JSS_TEST_BROWSER"))
                    ? "http://127.0.0.1:9103/static/logo.png"
                    : "/static/logo.png";

    @TestFactory
    Stream<DynamicTest> actualMallBrowserAcceptance() {
        if (!"1".equals(System.getenv("JSS_TEST_BROWSER"))) return Stream.empty();
        return Stream.of(
                DynamicTest.dynamicTest(
                        "realMallCartCheckoutAndCancellation",
                        () -> {
                            var frontend =
                                    Path.of(System.getenv("JSS_TEST_FRONTEND_DIR")).toRealPath();
                            assertTrue(
                                    Files.isRegularFile(
                                            frontend.resolve("playwright.mall.config.ts")));
                            var log =
                                    Path.of(
                                            System.getenv("JSS_TEST_RUNTIME_DIR"),
                                            "mall-browser.log");
                            boolean windows =
                                    System.getProperty("os.name").toLowerCase().contains("win");
                            var builder =
                                    windows
                                            ? new ProcessBuilder(
                                                    "cmd.exe",
                                                    "/d",
                                                    "/c",
                                                    "corepack.cmd",
                                                    "pnpm",
                                                    "test:mall")
                                            : new ProcessBuilder("corepack", "pnpm", "test:mall");
                            builder.environment()
                                    .put(
                                            "JSS_TEST_HTTP_API_URL",
                                            "http://127.0.0.1:"
                                                    + environment.getRequiredProperty(
                                                            "local.server.port"));
                            builder.environment().put("JSS_TEST_MALL_SPU_ID", String.valueOf(spu));
                            builder.environment().put("JSS_TEST_MALL_PRODUCT_NAME", name);
                            builder.directory(frontend.toFile())
                                    .redirectErrorStream(true)
                                    .redirectOutput(log.toFile());
                            var process = builder.start();
                            try {
                                assertTrue(
                                        process.waitFor(240, TimeUnit.SECONDS),
                                        "Mall browser acceptance timed out");
                                assertEquals(
                                        0,
                                        process.exitValue(),
                                        "Mall browser acceptance failed; inspect ignored mall-browser.log");
                            } finally {
                                if (process.isAlive()) {
                                    process.descendants().forEach(child -> child.destroyForcibly());
                                    process.destroyForcibly().waitFor();
                                }
                                if (Files.exists(log)) {
                                    String output = Files.readString(log);
                                    for (String key :
                                            List.of(
                                                    "JSS_TEST_HTTP_PASSWORD",
                                                    "JSS_TEST_MYSQL_PASSWORD",
                                                    "JSS_TEST_REDIS_PASSWORD")) {
                                        String secret = System.getenv(key);
                                        if (secret != null && !secret.isEmpty())
                                            output = output.replace(secret, "<redacted>");
                                    }
                                    Files.writeString(log, output);
                                }
                            }
                        }));
    }

    @DynamicPropertySource
    static void ownedConnections(DynamicPropertyRegistry registry) {
        for (String domain : List.of("master", "slave")) {
            String prefix = "spring.datasource.dynamic.datasource." + domain;
            registry.add(prefix + ".url", () -> System.getenv("JSS_TEST_MYSQL_URL"));
            registry.add(prefix + ".username", () -> System.getenv("JSS_TEST_MYSQL_USER"));
            registry.add(prefix + ".password", () -> System.getenv("JSS_TEST_MYSQL_PASSWORD"));
        }
    }

    private void assertOwnedInfrastructure() throws Exception {
        assertFalse(security.getMockEnable());
        try (var connection = datasource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT DATABASE()")) {
            assertTrue(rows.next());
            assertEquals(System.getenv("JSS_TEST_HTTP_SCHEMA"), rows.getString(1));
        }
        try (var connection = redis.getConnectionFactory().getConnection()) {
            assertEquals(
                    System.getenv("JSS_TEST_REDIS_RUN_ID"),
                    connection.serverCommands().info("server").getProperty("run_id"));
        }
    }

    private Map<String, Object> api(
            String method, String path, Object body, String token, long tenant) throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(15))
                        .header("tenant-id", String.valueOf(tenant))
                        .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(body)));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return JsonUtils.parseObject(response.body(), new TypeReference<>() {});
    }

    private Object ok(Map<String, Object> result) {
        assertEquals(0, number(result.get("code")), String.valueOf(result.get("msg")));
        return result.get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Map<String, Object> get(String path, String token) throws Exception {
        return object(ok(api("GET", path, null, token, 1)));
    }

    private void rejected(Map<String, Object> result) {
        long code = number(result.get("code"));
        assertTrue(
                code != 0 && code != 500, "Expected a controlled business rejection, got " + code);
    }

    private String login(boolean member, String account) throws Exception {
        var result =
                object(
                        ok(
                                api(
                                        "POST",
                                        member
                                                ? "/app-api/member/auth/login"
                                                : "/admin-api/system/auth/login",
                                        Map.of(
                                                member ? "mobile" : "username",
                                                account,
                                                "password",
                                                System.getenv("JSS_TEST_HTTP_PASSWORD")),
                                        null,
                                        1)));
        String token = (String) result.get("accessToken");
        assertNotNull(token);
        if (member) memberTokens.add(token);
        return token;
    }

    @BeforeAll
    void configureThroughProductionApis() throws Exception {
        assertOwnedInfrastructure();
        admin = login(false, "jssitreviewer");
        buyer = login(true, "13800009006");
        other = login(true, "13800009007");
        var config = new HashMap<String, Object>();
        config.put("afterSaleRefundReasons", List.of("隔离测试退款"));
        config.put("afterSaleReturnReasons", List.of("隔离测试退货"));
        config.put("deliveryExpressFreeEnabled", true);
        config.put("deliveryExpressFreePrice", 0);
        config.put("deliveryPickUpEnabled", false);
        config.put("brokerageEnabled", false);
        config.put("brokerageEnabledCondition", 2);
        config.put("brokerageBindMode", 1);
        config.put("brokeragePosterUrls", List.of());
        for (String key :
                List.of(
                        "brokerageFirstPercent",
                        "brokerageSecondPercent",
                        "brokerageWithdrawMinPrice",
                        "brokerageWithdrawFeePercent",
                        "brokerageFrozenDays")) config.put(key, 0);
        config.put("brokerageWithdrawTypes", List.of(2));
        ok(api("PUT", "/admin-api/trade/config/save", config, admin, 1));
        String callback =
                "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        long payApp =
                number(
                        ok(
                                api(
                                        "POST",
                                        "/admin-api/pay/app/create",
                                        Map.of(
                                                "appKey",
                                                "mall",
                                                "name",
                                                "隔离商城验收",
                                                "status",
                                                0,
                                                "orderNotifyUrl",
                                                callback + "/app-api/trade/order/update-paid",
                                                "refundNotifyUrl",
                                                callback
                                                        + "/admin-api/trade/after-sale/update-refunded"),
                                        admin,
                                        1)));
        ok(
                api(
                        "POST",
                        "/admin-api/pay/channel/create",
                        Map.of(
                                "appId",
                                payApp,
                                "code",
                                "wallet",
                                "status",
                                0,
                                "feeRate",
                                0D,
                                "config",
                                "{\"name\":\"isolated-wallet\"}"),
                        admin,
                        1));
        carrier =
                number(
                        ok(
                                api(
                                        "POST",
                                        "/admin-api/trade/delivery/express/create",
                                        Map.of(
                                                "code", "JSSIT", "name", "隔离测试物流", "sort", 1,
                                                "status", 0),
                                        admin,
                                        1)));
        address = createAddress(buyer, "买家甲");
        otherAddress = createAddress(other, "买家乙");
    }

    private long createAddress(String token, String receiver) throws Exception {
        return number(
                ok(
                        api(
                                "POST",
                                "/app-api/member/address/create",
                                Map.of(
                                        "name",
                                        receiver,
                                        "mobile",
                                        "13800009006",
                                        "areaId",
                                        110101,
                                        "detailAddress",
                                        "隔离测试地址1号",
                                        "defaultStatus",
                                        true),
                                token,
                                1)));
    }

    @BeforeEach
    void createIndependentProduct() throws Exception {
        assertOwnedInfrastructure();
        for (String token : List.of(buyer, other)) {
            var carts = get("/app-api/trade/cart/list", token);
            var ownedItems = new ArrayList<>(list(carts.get("validList")));
            ownedItems.addAll(list(carts.get("invalidList")));
            for (var item : ownedItems) {
                ok(
                        api(
                                "DELETE",
                                "/app-api/trade/cart/delete?ids=" + item.get("id"),
                                null,
                                token,
                                1));
            }
        }
        name = "JSSMall" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long parent = createCategory(0, name + "P");
        long category = createCategory(parent, name + "C");
        long brand =
                number(
                        ok(
                                api(
                                        "POST",
                                        "/admin-api/product/brand/create",
                                        Map.of(
                                                "name", name, "picUrl", image, "sort", 1, "status",
                                                0),
                                        admin,
                                        1)));
        var product = new HashMap<String, Object>();
        product.put("name", name);
        product.put("keyword", name);
        product.put("introduction", "真实接口隔离验收商品");
        product.put("description", "<p>JSS 商城真实接口验收</p>");
        product.put("categoryId", category);
        product.put("brandId", brand);
        product.put("picUrl", image);
        product.put("sliderPicUrls", List.of(image));
        product.put("sort", 1);
        product.put("specType", false);
        product.put("deliveryTypes", List.of(1));
        product.put("giveIntegral", 0);
        product.put("subCommissionType", false);
        product.put("virtualSalesCount", 0);
        product.put(
                "skus",
                List.of(
                        Map.of(
                                "name",
                                name,
                                "price",
                                1299,
                                "marketPrice",
                                1599,
                                "costPrice",
                                800,
                                "picUrl",
                                image,
                                "stock",
                                8,
                                "firstBrokeragePrice",
                                0,
                                "secondBrokeragePrice",
                                0)));
        spu = number(ok(api("POST", "/admin-api/product/spu/create", product, admin, 1)));
        sku =
                number(
                        list(get("/admin-api/product/spu/get-detail?id=" + spu, admin).get("skus"))
                                .get(0)
                                .get("id"));
    }

    private long createCategory(long parent, String categoryName) throws Exception {
        return number(
                ok(
                        api(
                                "POST",
                                "/admin-api/product/category/create",
                                Map.of(
                                        "parentId",
                                        parent,
                                        "name",
                                        categoryName,
                                        "picUrl",
                                        image,
                                        "sort",
                                        1,
                                        "status",
                                        0),
                                admin,
                                1)));
    }

    private long addCart(String token, int count) throws Exception {
        return number(
                ok(
                        api(
                                "POST",
                                "/app-api/trade/cart/add",
                                Map.of("skuId", sku, "count", count),
                                token,
                                1)));
    }

    private int stock() throws Exception {
        return (int)
                number(
                        list(get("/admin-api/product/spu/get-detail?id=" + spu, admin).get("skus"))
                                .get(0)
                                .get("stock"));
    }

    @Test
    void publicCatalogHonorsTenantAndShelfStatus() throws Exception {
        var page = get("/app-api/product/spu/page?pageNo=1&pageSize=10&keyword=" + name, null);
        assertTrue(list(page.get("list")).stream().anyMatch(item -> number(item.get("id")) == spu));
        var detail = get("/app-api/product/spu/get-detail?id=" + spu, null);
        assertEquals(1299, number(detail.get("price")));
        assertEquals(sku, number(list(detail.get("skus")).get(0).get("id")));
        rejected(api("GET", "/app-api/product/spu/get-detail?id=" + spu, null, null, 91001));
        ok(
                api(
                        "PUT",
                        "/admin-api/product/spu/update-status",
                        Map.of("id", spu, "status", 0),
                        admin,
                        1));
        rejected(api("GET", "/app-api/product/spu/get-detail?id=" + spu, null, null, 1));
        assertEquals(
                0,
                number(
                        get("/app-api/product/spu/page?pageNo=1&pageSize=10&keyword=" + name, null)
                                .get("total")));
        rejected(
                api("POST", "/app-api/trade/cart/add", Map.of("skuId", sku, "count", 1), buyer, 1));
        ok(
                api(
                        "PUT",
                        "/admin-api/product/spu/update-status",
                        Map.of("id", spu, "status", 1),
                        admin,
                        1));
        assertEquals(spu, number(get("/app-api/product/spu/get-detail?id=" + spu, null).get("id")));
    }

    @Test
    void cartMergesCountsChecksStockAndScopesEveryMutationToOwner() throws Exception {
        long cart = addCart(buyer, 1);
        assertEquals(cart, addCart(buyer, 2));
        assertEquals(3, number(ok(api("GET", "/app-api/trade/cart/get-count", null, buyer, 1))));
        rejected(
                api(
                        "POST",
                        "/app-api/trade/cart/add",
                        Map.of("skuId", sku, "count", Integer.MAX_VALUE),
                        buyer,
                        1));
        rejected(
                api(
                        "PUT",
                        "/app-api/trade/cart/update-count",
                        Map.of("id", cart, "count", 9),
                        buyer,
                        1));
        rejected(
                api(
                        "PUT",
                        "/app-api/trade/cart/update-count",
                        Map.of("id", cart, "count", 1),
                        other,
                        1));
        ok(
                api(
                        "PUT",
                        "/app-api/trade/cart/update-selected",
                        Map.of("ids", List.of(cart), "selected", false),
                        other,
                        1));
        ok(api("DELETE", "/app-api/trade/cart/delete?ids=" + cart, null, other, 1));
        var item = list(get("/app-api/trade/cart/list", buyer).get("validList")).get(0);
        assertEquals(cart, number(item.get("id")));
        assertEquals(3, number(item.get("count")));
        assertEquals(true, item.get("selected"));
        assertEquals(0, number(ok(api("GET", "/app-api/trade/cart/get-count", null, other, 1))));
        ok(
                api(
                        "PUT",
                        "/app-api/trade/cart/update-count",
                        Map.of("id", cart, "count", 2),
                        buyer,
                        1));
        ok(api("DELETE", "/app-api/trade/cart/delete?ids=" + cart, null, buyer, 1));
        assertEquals(0, number(ok(api("GET", "/app-api/trade/cart/get-count", null, buyer, 1))));
        assertEquals(8, stock());
    }

    private List<Map<String, Object>> parallelCartAdds(int requests) throws Exception {
        List<Callable<Map<String, Object>>> calls = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            calls.add(
                    () ->
                            api(
                                    "POST",
                                    "/app-api/trade/cart/add",
                                    Map.of("skuId", sku, "count", 1),
                                    buyer,
                                    1));
        }
        return parallelRequests(calls);
    }

    private List<Map<String, Object>> parallelRequests(List<Callable<Map<String, Object>>> calls)
            throws Exception {
        int requests = calls.size();
        var ready = new CountDownLatch(requests);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(requests);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        try {
            for (var call : calls) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(10, TimeUnit.SECONDS));
                                    return call.call();
                                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Map<String, Object>> responses = new ArrayList<>();
            for (var future : futures) responses.add(future.get(20, TimeUnit.SECONDS));
            return responses;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentCartAddsPreserveEveryAcceptedIncrementAndStockLimit() throws Exception {
        long cart = addCart(buyer, 1);
        for (var response : parallelCartAdds(6)) assertEquals(cart, number(ok(response)));
        var items = list(get("/app-api/trade/cart/list", buyer).get("validList"));
        assertEquals(1, items.size());
        assertEquals(
                7, number(items.get(0).get("count")), "Concurrent accepted increments were lost");

        var responses = parallelCartAdds(6);
        assertEquals(
                1,
                responses.stream().filter(response -> number(response.get("code")) == 0).count());
        for (var response : responses) {
            if (number(response.get("code")) == 0) assertEquals(cart, number(ok(response)));
            else rejected(response);
        }
        assertEquals(
                8,
                number(
                        list(get("/app-api/trade/cart/list", buyer).get("validList"))
                                .get(0)
                                .get("count")));
        ok(api("DELETE", "/app-api/trade/cart/delete?ids=" + cart, null, buyer, 1));

        responses = parallelCartAdds(6);
        long created = number(ok(responses.get(0)));
        for (var response : responses) assertEquals(created, number(ok(response)));
        items = list(get("/app-api/trade/cart/list", buyer).get("validList"));
        assertEquals(1, items.size());
        assertEquals(6, number(items.get(0).get("count")));
        assertEquals(8, stock());
    }

    private Map<String, Object> orderBody(List<?> items) {
        var body = new HashMap<String, Object>();
        body.put("items", items);
        body.put("pointStatus", false);
        body.put("deliveryType", 1);
        body.put("addressId", address);
        return body;
    }

    @Test
    void claimedCouponDiscountsOwnedOrdersAndCancellationReturnsItForReuse() throws Exception {
        String couponName = name + "Coupon";
        var template = new HashMap<String, Object>();
        template.put("name", couponName);
        template.put("description", "隔离 HTTP 优惠券下单与取消返还");
        template.put("totalCount", 2);
        template.put("takeLimitCount", 1);
        template.put("takeType", 1);
        template.put("usePrice", 1000);
        template.put("productScope", 2);
        template.put("productScopeValues", List.of(spu));
        template.put("validityType", 1);
        var format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        template.put("validStartTime", LocalDateTime.now().minusHours(1).format(format));
        template.put("validEndTime", LocalDateTime.now().plusDays(1).format(format));
        template.put("discountType", 1);
        template.put("discountPrice", 200);
        long templateId =
                number(
                        ok(
                                api(
                                        "POST",
                                        "/admin-api/promotion/coupon-template/create",
                                        template,
                                        admin,
                                        1)));
        assertEquals(
                true,
                get("/app-api/promotion/coupon-template/get?id=" + templateId, buyer)
                        .get("canTake"));
        assertEquals(
                false,
                ok(
                        api(
                                "POST",
                                "/app-api/promotion/coupon/take",
                                Map.of("templateId", templateId),
                                buyer,
                                1)));
        rejected(
                api(
                        "POST",
                        "/app-api/promotion/coupon/take",
                        Map.of("templateId", templateId),
                        buyer,
                        1));
        assertEquals(
                false,
                get("/app-api/promotion/coupon-template/get?id=" + templateId, buyer)
                        .get("canTake"));
        var coupon =
                list(
                                get(
                                                "/app-api/promotion/coupon/page?pageNo=1&pageSize=100&status=1",
                                                buyer)
                                        .get("list"))
                        .stream()
                        .filter(item -> couponName.equals(item.get("name")))
                        .findFirst()
                        .orElseThrow();
        long couponId = number(coupon.get("id"));
        assertNull(ok(api("GET", "/app-api/promotion/coupon/get?id=" + couponId, null, other, 1)));
        var before = orderFootprint();
        var body = orderBody(List.of(Map.of("skuId", sku, "count", 1)));
        body.put("couponId", couponId);
        body.put("addressId", otherAddress);
        rejected(api("POST", "/app-api/trade/order/create", body, other, 1));
        assertEquals(before, orderFootprint());
        assertInventory(8);
        body.put("addressId", address);
        var settlement =
                get(
                        "/app-api/trade/order/settlement?items%5B0%5D.skuId="
                                + sku
                                + "&items%5B0%5D.count=1&pointStatus=false&deliveryType=1&addressId="
                                + address
                                + "&couponId="
                                + couponId,
                        buyer);
        assertEquals(1099, number(object(settlement.get("price")).get("payPrice")));
        assertEquals(200, number(object(settlement.get("price")).get("couponPrice")));
        body.put("payPrice", 1);
        for (int attempt = 0; attempt < 2; attempt++) {
            var created = object(ok(api("POST", "/app-api/trade/order/create", body, buyer, 1)));
            long order = number(created.get("id"));
            var detail = get("/app-api/trade/order/get-detail?id=" + order, buyer);
            assertEquals(1099, number(detail.get("payPrice")));
            assertEquals(200, number(detail.get("couponPrice")));
            assertEquals(
                    1099,
                    number(
                            get("/app-api/pay/order/get?id=" + created.get("payOrderId"), buyer)
                                    .get("price")));
            assertEquals(
                    2,
                    number(
                            get("/app-api/promotion/coupon/get?id=" + couponId, buyer)
                                    .get("status")));
            assertEquals(
                    order,
                    scalar("SELECT use_order_id FROM promotion_coupon WHERE id=" + couponId));
            assertInventory(7);
            var after = orderFootprint();
            rejected(api("POST", "/app-api/trade/order/create", body, buyer, 1));
            assertEquals(after, orderFootprint());
            assertInventory(7);
            ok(api("DELETE", "/app-api/trade/order/cancel?id=" + order, null, buyer, 1));
            assertEquals(
                    1,
                    number(
                            get("/app-api/promotion/coupon/get?id=" + couponId, buyer)
                                    .get("status")));
            assertInventory(8);
            rejected(api("DELETE", "/app-api/trade/order/cancel?id=" + order, null, buyer, 1));
            assertInventory(8);
        }
        assertEquals(
                1,
                number(
                        get("/admin-api/promotion/coupon-template/get?id=" + templateId, admin)
                                .get("takeCount")));
        ok(
                api(
                        "PUT",
                        "/admin-api/promotion/coupon-template/update-status",
                        Map.of("id", templateId, "status", 1),
                        admin,
                        1));
        assertEquals(
                false,
                get("/app-api/promotion/coupon-template/get?id=" + templateId, other)
                        .get("canTake"));
        assertEquals(
                1_013_004_006,
                number(
                        api(
                                        "POST",
                                        "/app-api/promotion/coupon/take",
                                        Map.of("templateId", templateId),
                                        other,
                                        1)
                                .get("code")));
    }

    private long scalar(String sql) throws Exception {
        try (var connection = datasource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private List<Long> orderFootprint() throws Exception {
        List<Long> counts = new ArrayList<>();
        for (String table :
                List.of("trade_order", "trade_order_item", "pay_order", "trade_order_log")) {
            counts.add(scalar("SELECT COUNT(*) FROM " + table));
        }
        return counts;
    }

    private void assertInventory(int expected) throws Exception {
        var detail = get("/admin-api/product/spu/get-detail?id=" + spu, admin);
        assertEquals(expected, number(detail.get("stock")), "SPU stock mismatch");
        assertEquals(expected, stock(), "SKU stock mismatch");
    }

    @Test
    void concurrentOrdersReserveStockOnceAndLoserCanRetryAfterCancellation() throws Exception {
        List<String> tokens = List.of(buyer, other);
        List<Long> carts = List.of(addCart(buyer, 5), addCart(other, 5));
        List<Callable<Map<String, Object>>> calls = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            var body = orderBody(List.of(Map.of("cartId", carts.get(i))));
            body.put("addressId", i == 0 ? address : otherAddress);
            calls.add(() -> api("POST", "/app-api/trade/order/create", body, token, 1));
        }
        var before = orderFootprint();
        var results = parallelRequests(calls);
        assertEquals(1, results.stream().filter(r -> number(r.get("code")) == 0).count());
        int winner = number(results.get(0).get("code")) == 0 ? 0 : 1;
        int loser = 1 - winner;
        rejected(results.get(loser));
        var created = object(ok(results.get(winner)));
        long order = number(created.get("id"));
        long payOrder = number(created.get("payOrderId"));
        var detail = get("/app-api/trade/order/get-detail?id=" + order, tokens.get(winner));
        assertEquals(6495, number(detail.get("payPrice")));
        assertEquals(5, number(detail.get("productCount")));
        assertEquals(0, number(detail.get("status")));
        assertEquals(1, list(detail.get("items")).size());
        var payment = get("/app-api/pay/order/get?id=" + payOrder, tokens.get(winner));
        assertEquals(6495, number(payment.get("price")));
        assertEquals(String.valueOf(order), payment.get("merchantOrderId"));
        assertEquals(0, number(payment.get("status")));
        var after = orderFootprint();
        for (int i = 0; i < before.size(); i++) assertEquals(before.get(i) + 1, after.get(i));
        assertInventory(3);
        assertEquals(
                0,
                number(
                        ok(
                                api(
                                        "GET",
                                        "/app-api/trade/cart/get-count",
                                        null,
                                        tokens.get(winner),
                                        1))));
        assertEquals(
                5,
                number(
                        ok(
                                api(
                                        "GET",
                                        "/app-api/trade/cart/get-count",
                                        null,
                                        tokens.get(loser),
                                        1))));
        rejected(
                api(
                        "DELETE",
                        "/app-api/trade/order/cancel?id=" + order,
                        null,
                        tokens.get(loser),
                        1));
        assertInventory(3);
        ok(api("DELETE", "/app-api/trade/order/cancel?id=" + order, null, tokens.get(winner), 1));
        assertInventory(8);
        rejected(
                api(
                        "DELETE",
                        "/app-api/trade/order/cancel?id=" + order,
                        null,
                        tokens.get(winner),
                        1));
        assertInventory(8);

        var retried = object(ok(calls.get(loser).call()));
        assertInventory(3);
        ok(
                api(
                        "DELETE",
                        "/app-api/trade/order/cancel?id=" + retried.get("id"),
                        null,
                        tokens.get(loser),
                        1));
        assertInventory(8);
    }

    @Test
    void duplicateSkuItemsAreRejectedWithoutPartialOrdersOrInventoryChanges() throws Exception {
        long cart = addCart(buyer, 2);
        var before = orderFootprint();
        for (var items :
                List.of(
                        List.of(Map.of("skuId", sku, "count", 5), Map.of("skuId", sku, "count", 5)),
                        List.of(Map.of("skuId", sku, "count", 2), Map.of("skuId", sku, "count", 3)),
                        List.of(Map.of("cartId", cart), Map.of("cartId", cart)),
                        List.of(Map.of("cartId", cart), Map.of("skuId", sku, "count", 1)))) {
            assertEquals(
                    1_011_000_040,
                    number(
                            api("POST", "/app-api/trade/order/create", orderBody(items), buyer, 1)
                                    .get("code")));
            assertInventory(8);
            assertEquals(before, orderFootprint(), "Rejected order left persistent side effects");
            assertEquals(
                    2, number(ok(api("GET", "/app-api/trade/cart/get-count", null, buyer, 1))));
        }
        assertEquals(
                1_011_000_040,
                number(
                        api(
                                        "GET",
                                        "/app-api/trade/order/settlement?items%5B0%5D.skuId="
                                                + sku
                                                + "&items%5B0%5D.count=2&items%5B1%5D.skuId="
                                                + sku
                                                + "&items%5B1%5D.count=3&pointStatus=false&deliveryType=1&addressId="
                                                + address,
                                        null,
                                        buyer,
                                        1)
                                .get("code")));
        assertInventory(8);
        assertEquals(before, orderFootprint());
    }

    @Test
    void settlementCreatesOwnedOrderAndCancellationRestoresStock() throws Exception {
        long cart = addCart(buyer, 2);
        var settlement =
                get(
                        "/app-api/trade/order/settlement?items%5B0%5D.cartId="
                                + cart
                                + "&pointStatus=false&deliveryType=1&addressId="
                                + address,
                        buyer);
        assertEquals(2598, number(object(settlement.get("price")).get("payPrice")));
        var body = orderBody(List.of(Map.of("cartId", cart)));
        body.put("payPrice", 1); // The server must calculate its own amount.
        var created = object(ok(api("POST", "/app-api/trade/order/create", body, buyer, 1)));
        long order = number(created.get("id"));
        assertTrue(number(created.get("payOrderId")) > 0);
        var detail = get("/app-api/trade/order/get-detail?id=" + order, buyer);
        assertEquals(2598, number(detail.get("payPrice")));
        assertEquals(0, number(detail.get("status")));
        assertEquals(6, stock());
        assertEquals(0, number(ok(api("GET", "/app-api/trade/cart/get-count", null, buyer, 1))));
        assertNull(ok(api("GET", "/app-api/trade/order/get-detail?id=" + order, null, other, 1)));
        rejected(api("DELETE", "/app-api/trade/order/cancel?id=" + order, null, other, 1));
        assertEquals(6, stock());
        ok(api("DELETE", "/app-api/trade/order/cancel?id=" + order, null, buyer, 1));
        assertEquals(
                40,
                number(get("/app-api/trade/order/get-detail?id=" + order, buyer).get("status")));
        assertEquals(8, stock());
        rejected(api("DELETE", "/app-api/trade/order/cancel?id=" + order, null, buyer, 1));
        assertEquals(8, stock());
    }

    @Test
    void invalidQuantityAndForeignAddressCannotCreateOrdersOrChangeStock() throws Exception {
        long existingOrders =
                number(get("/app-api/trade/order/page?pageNo=1&pageSize=10", buyer).get("total"));
        for (int count : List.of(0, -1, 9)) {
            var body = orderBody(List.of(Map.of("skuId", sku, "count", count)));
            var result = api("POST", "/app-api/trade/order/create", body, buyer, 1);
            if (count <= 0)
                assertEquals(
                        400,
                        number(result.get("code")),
                        "Non-positive quantity must fail validation");
            else rejected(result);
            assertEquals(8, stock());
        }
        var body = orderBody(List.of(Map.of("skuId", sku, "count", 1)));
        body.put("addressId", otherAddress);
        assertEquals(
                1004004000,
                number(api("POST", "/app-api/trade/order/create", body, buyer, 1).get("code")));
        body.remove("addressId");
        assertEquals(
                1004004000,
                number(api("POST", "/app-api/trade/order/create", body, buyer, 1).get("code")));
        for (var malformed :
                List.of(
                        orderBody(List.of()),
                        orderBody(java.util.Arrays.asList((Object) null)),
                        orderBody(List.of(Map.of("skuId", sku))))) {
            assertEquals(
                    400,
                    number(
                            api("POST", "/app-api/trade/order/create", malformed, buyer, 1)
                                    .get("code")));
        }
        assertEquals(
                400,
                number(
                        api(
                                        "GET",
                                        "/app-api/trade/order/settlement?items%5B0%5D.skuId="
                                                + sku
                                                + "&items%5B0%5D.count=0&pointStatus=false",
                                        null,
                                        buyer,
                                        1)
                                .get("code")));
        long foreignCart = addCart(other, 1);
        rejected(
                api(
                        "POST",
                        "/app-api/trade/order/create",
                        orderBody(List.of(Map.of("cartId", foreignCart))),
                        buyer,
                        1));
        assertEquals(8, stock());
        assertEquals(
                existingOrders,
                number(get("/app-api/trade/order/page?pageNo=1&pageSize=10", buyer).get("total")));
    }

    private Map<String, Object> awaitField(String path, String token, String field, long expected)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        Map<String, Object> result;
        do {
            result = get(path, token);
            if (number(result.get(field)) == expected) return result;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertEquals(expected, number(result.get(field)), "Timed out waiting for " + field);
        return result;
    }

    private void awaitNotify(int type, long dataId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        do {
            var tasks =
                    list(
                            get(
                                            "/admin-api/pay/notify/page?pageNo=1&pageSize=10&type="
                                                    + type
                                                    + "&dataId="
                                                    + dataId,
                                            admin)
                                    .get("list"));
            if (tasks.size() == 1 && number(tasks.get(0).get("status")) == 10) {
                assertEquals(1, number(tasks.get(0).get("notifyTimes")));
                return;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        fail("Expected one successful real HTTP payment callback");
    }

    private long walletBalance(String token) throws Exception {
        return number(get("/app-api/pay/wallet/get", token).get("balance"));
    }

    private Map<String, Object> ledger(String token) throws Exception {
        return get("/app-api/pay/wallet-transaction/page?pageNo=1&pageSize=100", token);
    }

    private record PaidOrder(
            long order, long payOrder, long balanceBeforePayment, long ledgerBeforePayment) {}

    private record WalletOrder(long id, long payOrder) {}

    private WalletOrder createUnpaidWalletOrder() throws Exception {
        var created =
                object(
                        ok(
                                api(
                                        "POST",
                                        "/app-api/trade/order/create",
                                        orderBody(List.of(Map.of("skuId", sku, "count", 1))),
                                        buyer,
                                        1)));
        return new WalletOrder(number(created.get("id")), number(created.get("payOrderId")));
    }

    private void setBuyerWalletBalance(int balance) throws Exception {
        int delta = Math.toIntExact(balance - walletBalance(buyer));
        if (delta != 0)
            ok(
                    api(
                            "PUT",
                            "/admin-api/pay/wallet/update-balance",
                            Map.of("userId", 23006, "balance", delta),
                            admin,
                            1));
        assertEquals(balance, walletBalance(buyer));
    }

    private Map<String, Object> submitWallet(WalletOrder order) throws Exception {
        return api(
                "POST",
                "/app-api/pay/order/submit",
                Map.of("id", order.payOrder(), "channelCode", "wallet"),
                buyer,
                1);
    }

    private void assertWalletOrderPaidOnce(WalletOrder order) throws Exception {
        var trade =
                awaitField("/app-api/trade/order/get-detail?id=" + order.id(), buyer, "status", 10);
        assertEquals(true, trade.get("payStatus"));
        assertEquals(order.payOrder(), number(trade.get("payOrderId")));
        assertEquals(1299, number(trade.get("payPrice")));
        var payment = get("/app-api/pay/order/get?id=" + order.payOrder(), buyer);
        assertEquals(10, number(payment.get("status")));
        assertEquals(1299, number(payment.get("price")));
        assertEquals(String.valueOf(order.id()), payment.get("merchantOrderId"));
        awaitNotify(1, order.payOrder());
        assertEquals(
                1,
                scalar(
                        "SELECT COUNT(*) FROM pay_order_extension WHERE order_id="
                                + order.payOrder()
                                + " AND status=10"));
        assertEquals(
                1,
                scalar(
                        "SELECT COUNT(*) FROM pay_wallet_transaction WHERE biz_type=3 AND biz_id='"
                                + order.payOrder()
                                + "' AND price=-1299"));
        assertEquals(
                1,
                scalar(
                        "SELECT COUNT(*) FROM trade_order_log WHERE order_id="
                                + order.id()
                                + " AND operate_type=10"));
        assertEquals(
                1,
                scalar(
                        "SELECT COUNT(*) FROM pay_notify_log l JOIN pay_notify_task t ON t.id=l.task_id"
                                + " WHERE t.type=1 AND t.data_id="
                                + order.payOrder()));
    }

    private void assertWalletOrderStillUnpaid(WalletOrder order) throws Exception {
        var trade = get("/app-api/trade/order/get-detail?id=" + order.id(), buyer);
        assertEquals(0, number(trade.get("status")));
        assertEquals(false, trade.get("payStatus"));
        assertEquals(
                0,
                number(get("/app-api/pay/order/get?id=" + order.payOrder(), buyer).get("status")));
        assertEquals(
                0,
                scalar(
                        "SELECT COUNT(*) FROM pay_wallet_transaction WHERE biz_type=3 AND biz_id='"
                                + order.payOrder()
                                + "'"));
        assertEquals(
                0,
                scalar(
                        "SELECT COUNT(*) FROM pay_order_extension WHERE order_id="
                                + order.payOrder()
                                + " AND status=10"));
        assertEquals(
                0,
                scalar(
                        "SELECT COUNT(*) FROM pay_notify_task WHERE type=1 AND data_id="
                                + order.payOrder()));
        assertEquals(
                0,
                scalar(
                        "SELECT COUNT(*) FROM trade_order_log WHERE operate_type=10 AND order_id="
                                + order.id()));
    }

    @Test
    void concurrentWalletSubmissionsOfOneOrderDebitAndNotifyExactlyOnce() throws Exception {
        setBuyerWalletBalance(
                12990); // Enough for every request: balance exhaustion cannot hide a duplicate
        // debit.
        var beforeWallet = get("/app-api/pay/wallet/get", buyer);
        long beforeLedger = number(ledger(buyer).get("total"));
        long otherBalance = walletBalance(other);
        var order = createUnpaidWalletOrder();
        List<Callable<Map<String, Object>>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) calls.add(() -> submitWallet(order));
        var results = parallelRequests(calls);
        assertEquals(1, results.stream().filter(r -> number(r.get("code")) == 0).count());
        for (var result : results) {
            if (number(result.get("code")) == 0)
                assertEquals(10, number(object(ok(result)).get("status")));
            else rejected(result);
        }
        assertWalletOrderPaidOnce(order);
        var afterWallet = get("/app-api/pay/wallet/get", buyer);
        assertEquals(11691, number(afterWallet.get("balance")));
        assertEquals(
                number(beforeWallet.get("totalExpense")) + 1299,
                number(afterWallet.get("totalExpense")));
        assertEquals(beforeLedger + 1, number(ledger(buyer).get("total")));
        assertEquals(otherBalance, walletBalance(other));
        assertInventory(7);
        rejected(submitWallet(order));
        assertWalletOrderPaidOnce(order);
        assertEquals(11691, walletBalance(buyer));
        assertEquals(beforeLedger + 1, number(ledger(buyer).get("total")));
    }

    @Test
    void competingWalletOrdersCannotOverdrawAndRejectedOrderCanBePaidAfterCredit()
            throws Exception {
        setBuyerWalletBalance(1299);
        var first = createUnpaidWalletOrder();
        var second = createUnpaidWalletOrder();
        var orders = List.of(first, second);
        long beforeLedger = number(ledger(buyer).get("total"));
        var beforeWallet = get("/app-api/pay/wallet/get", buyer);
        var results =
                parallelRequests(List.of(() -> submitWallet(first), () -> submitWallet(second)));
        assertEquals(1, results.stream().filter(r -> number(r.get("code")) == 0).count());
        int winner = number(results.get(0).get("code")) == 0 ? 0 : 1;
        int loser = 1 - winner;
        assertEquals(10, number(object(ok(results.get(winner))).get("status")));
        rejected(results.get(loser));
        assertWalletOrderPaidOnce(orders.get(winner));
        assertWalletOrderStillUnpaid(orders.get(loser));
        assertEquals(0, walletBalance(buyer));
        assertEquals(beforeLedger + 1, number(ledger(buyer).get("total")));
        assertEquals(
                number(beforeWallet.get("totalExpense")) + 1299,
                number(get("/app-api/pay/wallet/get", buyer).get("totalExpense")));
        assertInventory(6);

        setBuyerWalletBalance(1299);
        long ledgerAfterCredit = number(ledger(buyer).get("total"));
        assertEquals(10, number(object(ok(submitWallet(orders.get(loser)))).get("status")));
        assertWalletOrderPaidOnce(orders.get(loser));
        assertWalletOrderPaidOnce(orders.get(winner));
        assertEquals(0, walletBalance(buyer));
        assertEquals(ledgerAfterCredit + 1, number(ledger(buyer).get("total")));
        assertEquals(
                number(beforeWallet.get("totalExpense")) + 2598,
                number(get("/app-api/pay/wallet/get", buyer).get("totalExpense")));
        assertInventory(6);
    }

    private PaidOrder createWalletPaidOrder() throws Exception {
        // Real administrative test credit; no external payment provider or fabricated pay status.
        ok(
                api(
                        "PUT",
                        "/admin-api/pay/wallet/update-balance",
                        Map.of("userId", 23006, "balance", 10000),
                        admin,
                        1));
        ok(
                api(
                        "PUT",
                        "/admin-api/pay/wallet/update-balance",
                        Map.of("userId", 23006, "balance", -50),
                        admin,
                        1));
        long beforePayment = walletBalance(buyer);
        long ledgerBefore = number(ledger(buyer).get("total"));
        var otherWallet = get("/admin-api/pay/wallet/get?userId=23007", admin);
        long otherBalance = number(otherWallet.get("balance"));
        var created =
                object(
                        ok(
                                api(
                                        "POST",
                                        "/app-api/trade/order/create",
                                        orderBody(List.of(Map.of("skuId", sku, "count", 1))),
                                        buyer,
                                        1)));
        long order = number(created.get("id"));
        long payOrder = number(created.get("payOrderId"));
        assertNull(ok(api("GET", "/app-api/pay/order/get?id=" + payOrder, null, other, 1)));
        rejected(
                api(
                        "POST",
                        "/app-api/pay/order/submit",
                        Map.of("id", payOrder, "channelCode", "wallet"),
                        other,
                        1));
        rejected(
                api(
                        "POST",
                        "/app-api/trade/order/update-paid",
                        Map.of("merchantOrderId", String.valueOf(order), "payOrderId", payOrder),
                        null,
                        1));
        assertEquals(
                0, number(get("/app-api/trade/order/get-detail?id=" + order, buyer).get("status")));
        var payment =
                object(
                        ok(
                                api(
                                        "POST",
                                        "/app-api/pay/order/submit",
                                        Map.of(
                                                "id",
                                                payOrder,
                                                "channelCode",
                                                "wallet",
                                                "channelExtras",
                                                Map.of(
                                                        "walletId",
                                                        String.valueOf(otherWallet.get("id")))),
                                        buyer,
                                        1)));
        assertEquals(10, number(payment.get("status")));
        var detail = awaitField("/app-api/trade/order/get-detail?id=" + order, buyer, "status", 10);
        assertEquals(true, detail.get("payStatus"));
        assertEquals(1299, number(detail.get("payPrice")));
        awaitNotify(1, payOrder);
        assertEquals(beforePayment - 1299, walletBalance(buyer));
        assertEquals(otherBalance, walletBalance(other));
        assertEquals(ledgerBefore + 1, number(ledger(buyer).get("total")));
        assertEquals(7, stock());
        rejected(
                api(
                        "POST",
                        "/app-api/pay/order/submit",
                        Map.of("id", payOrder, "channelCode", "wallet"),
                        buyer,
                        1));
        ok(
                api(
                        "POST",
                        "/app-api/trade/order/update-paid",
                        Map.of("merchantOrderId", String.valueOf(order), "payOrderId", payOrder),
                        null,
                        1));
        assertEquals(beforePayment - 1299, walletBalance(buyer));
        assertEquals(ledgerBefore + 1, number(ledger(buyer).get("total")));
        return new PaidOrder(order, payOrder, beforePayment, ledgerBefore);
    }

    private long applyAfterSale(PaidOrder paid, int way) throws Exception {
        var detail = get("/app-api/trade/order/get-detail?id=" + paid.order(), buyer);
        long item = number(list(detail.get("items")).get(0).get("id"));
        var body =
                Map.of(
                        "orderItemId",
                        item,
                        "way",
                        way,
                        "refundPrice",
                        1299,
                        "applyReason",
                        "隔离测试退款",
                        "applyDescription",
                        "实际钱包退款验收");
        rejected(api("POST", "/app-api/trade/after-sale/create", body, other, 1));
        rejected(
                api(
                        "POST",
                        "/app-api/trade/after-sale/create",
                        Map.of(
                                "orderItemId",
                                item,
                                "way",
                                way,
                                "refundPrice",
                                1300,
                                "applyReason",
                                "隔离测试退款"),
                        buyer,
                        1));
        long id = number(ok(api("POST", "/app-api/trade/after-sale/create", body, buyer, 1)));
        assertNull(ok(api("GET", "/app-api/trade/after-sale/get?id=" + id, null, other, 1)));
        rejected(api("DELETE", "/app-api/trade/after-sale/cancel?id=" + id, null, other, 1));
        assertEquals(
                10, number(get("/app-api/trade/after-sale/get?id=" + id, buyer).get("status")));
        return id;
    }

    private void finishWalletRefund(PaidOrder paid, long afterSale) throws Exception {
        ok(api("PUT", "/admin-api/trade/after-sale/refund?id=" + afterSale, null, admin, 1));
        awaitField("/app-api/trade/after-sale/get?id=" + afterSale, buyer, "status", 50);
        long refundId =
                number(
                        get("/admin-api/trade/after-sale/get-detail?id=" + afterSale, admin)
                                .get("payRefundId"));
        awaitNotify(2, refundId);
        var detail = get("/app-api/trade/order/get-detail?id=" + paid.order(), buyer);
        assertEquals(40, number(detail.get("status")));
        assertEquals(20, number(detail.get("refundStatus")));
        assertEquals(1299, number(detail.get("refundPrice")));
        assertEquals(paid.balanceBeforePayment(), walletBalance(buyer));
        assertEquals(paid.ledgerBeforePayment() + 2, number(ledger(buyer).get("total")));
        var transactions = list(ledger(buyer).get("list"));
        assertEquals(4, number(transactions.get(0).get("bizType")));
        assertEquals(1299, number(transactions.get(0).get("price")));
        assertEquals(3, number(transactions.get(1).get("bizType")));
        assertEquals(-1299, number(transactions.get(1).get("price")));
        assertEquals(8, stock());
        rejected(api("PUT", "/admin-api/trade/after-sale/refund?id=" + afterSale, null, admin, 1));
        rejected(
                api(
                        "POST",
                        "/admin-api/trade/after-sale/update-refunded",
                        Map.of(
                                "merchantOrderId",
                                String.valueOf(paid.order()),
                                "merchantRefundId",
                                String.valueOf(afterSale),
                                "payRefundId",
                                refundId),
                        null,
                        1));
        assertEquals(paid.balanceBeforePayment(), walletBalance(buyer));
        assertEquals(paid.ledgerBeforePayment() + 2, number(ledger(buyer).get("total")));
        assertEquals(8, stock());
    }

    @Test
    void walletPaymentAndPreShipmentRefundUseRealLedgerAndCallbacks() throws Exception {
        PaidOrder paid = createWalletPaidOrder();
        rejected(api("DELETE", "/app-api/trade/order/cancel?id=" + paid.order(), null, buyer, 1));
        assertEquals(7, stock());
        long afterSale = applyAfterSale(paid, 10);
        ok(api("PUT", "/admin-api/trade/after-sale/agree?id=" + afterSale, null, admin, 1));
        assertEquals(
                40,
                number(get("/app-api/trade/after-sale/get?id=" + afterSale, buyer).get("status")));
        finishWalletRefund(paid, afterSale);
    }

    @Test
    void paidOrderDeliveryReceiptAndReturnRefundRestoreWalletAndInventory() throws Exception {
        PaidOrder paid = createWalletPaidOrder();
        rejected(api("PUT", "/app-api/trade/order/receive?id=" + paid.order(), null, buyer, 1));
        ok(
                api(
                        "PUT",
                        "/admin-api/trade/order/delivery",
                        Map.of(
                                "id",
                                paid.order(),
                                "logisticsId",
                                carrier,
                                "logisticsNo",
                                "JSS" + name),
                        admin,
                        1));
        assertEquals(
                20,
                number(
                        get("/app-api/trade/order/get-detail?id=" + paid.order(), buyer)
                                .get("status")));
        rejected(api("PUT", "/app-api/trade/order/receive?id=" + paid.order(), null, other, 1));
        ok(api("PUT", "/app-api/trade/order/receive?id=" + paid.order(), null, buyer, 1));
        assertEquals(
                30,
                number(
                        get("/app-api/trade/order/get-detail?id=" + paid.order(), buyer)
                                .get("status")));
        long afterSale = applyAfterSale(paid, 20);
        ok(api("PUT", "/admin-api/trade/after-sale/agree?id=" + afterSale, null, admin, 1));
        assertEquals(
                20,
                number(get("/app-api/trade/after-sale/get?id=" + afterSale, buyer).get("status")));
        var delivery =
                Map.of("id", afterSale, "logisticsId", carrier, "logisticsNo", "RETURN" + name);
        rejected(api("PUT", "/app-api/trade/after-sale/delivery", delivery, other, 1));
        ok(api("PUT", "/app-api/trade/after-sale/delivery", delivery, buyer, 1));
        assertEquals(
                30,
                number(get("/app-api/trade/after-sale/get?id=" + afterSale, buyer).get("status")));
        ok(api("PUT", "/admin-api/trade/after-sale/receive?id=" + afterSale, null, admin, 1));
        assertEquals(
                40,
                number(get("/app-api/trade/after-sale/get?id=" + afterSale, buyer).get("status")));
        finishWalletRefund(paid, afterSale);
    }

    @Test
    void insufficientWalletBalanceKeepsOrderUnpaidAndCanStillCancel() throws Exception {
        long balanceBefore = walletBalance(other);
        assertEquals(0, balanceBefore);
        long ledgerBefore = number(ledger(other).get("total"));
        var body = orderBody(List.of(Map.of("skuId", sku, "count", 1)));
        body.put("addressId", otherAddress);
        var created = object(ok(api("POST", "/app-api/trade/order/create", body, other, 1)));
        long order = number(created.get("id"));
        long payOrder = number(created.get("payOrderId"));
        rejected(
                api(
                        "POST",
                        "/app-api/pay/order/submit",
                        Map.of("id", payOrder, "channelCode", "wallet"),
                        other,
                        1));
        assertEquals(balanceBefore, walletBalance(other));
        assertEquals(ledgerBefore, number(ledger(other).get("total")));
        assertEquals(
                0, number(get("/app-api/trade/order/get-detail?id=" + order, other).get("status")));
        assertEquals(0, number(get("/app-api/pay/order/get?id=" + payOrder, other).get("status")));
        assertEquals(7, stock());
        ok(api("DELETE", "/app-api/trade/order/cancel?id=" + order, null, other, 1));
        assertEquals(8, stock());
    }

    @AfterAll
    void logoutFixtureTokens() throws Exception {
        for (String token : memberTokens) api("POST", "/app-api/jss/auth/logout", null, token, 1);
        if (admin != null) api("POST", "/admin-api/system/auth/logout", null, admin, 1);
    }
}
