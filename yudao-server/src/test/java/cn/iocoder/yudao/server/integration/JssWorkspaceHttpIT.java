package cn.iocoder.yudao.server.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.module.jss.framework.JssProperties;
import cn.iocoder.yudao.server.YudaoServerApplication;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.core.type.TypeReference;

import javax.sql.DataSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Full production server/security chain, with real HTTP/MySQL/Redis and disposable identities. */
@EnabledIfEnvironmentVariable(named = "JSS_TEST_HTTP_SCHEMA", matches = "jss_http_verify_[0-9a-f]{12}")
@ContextConfiguration(initializers = JssHttpFixtures.class)
@SpringBootTest(classes = YudaoServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=local", "server.address=127.0.0.1",
                "spring.quartz.auto-startup=false", "spring.boot.admin.client.enabled=false", "xxl.job.enabled=false",
                "yudao.captcha.enable=false", "yudao.security.mock-enable=false", "jss.workspace.enabled=true",
                "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=16379", "spring.data.redis.database=14",
                "spring.data.redis.password=${JSS_TEST_REDIS_PASSWORD}",
                "logging.file.name=${JSS_TEST_RUNTIME_DIR}/server.log", "logging.file.path=${JSS_TEST_RUNTIME_DIR}",
                "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
class JssWorkspaceHttpIT {
    @Autowired private Environment environment;
    @Autowired private DataSource datasource;
    @Autowired private StringRedisTemplate redis;
    @Autowired private SecurityProperties security;
    @Autowired private JssProperties workspace;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<Map.Entry<String, String>> tokens = new ArrayList<>();

    @DynamicPropertySource
    static void ownedConnections(DynamicPropertyRegistry registry) {
        for (String domain : List.of("master", "slave")) {
            String prefix = "spring.datasource.dynamic.datasource." + domain;
            registry.add(prefix + ".url", () -> System.getenv("JSS_TEST_MYSQL_URL"));
            registry.add(prefix + ".username", () -> System.getenv("JSS_TEST_MYSQL_USER"));
            registry.add(prefix + ".password", () -> System.getenv("JSS_TEST_MYSQL_PASSWORD"));
        }
    }

    @BeforeEach
    void assertRealOwnedInfrastructure() throws Exception {
        assertFalse(security.getMockEnable());
        assertTrue(workspace.isEnabled());
        try (var connection = datasource.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT DATABASE()")) {
            assertTrue(rows.next());
            assertEquals(System.getenv("JSS_TEST_HTTP_SCHEMA"), rows.getString(1));
        }
        try (var connection = redis.getConnectionFactory().getConnection()) {
            assertEquals(System.getenv("JSS_TEST_REDIS_RUN_ID"), connection.serverCommands().info("server").getProperty("run_id"));
        }
    }

    private Map<String, Object> api(String method, String path, Object body, String token, long tenant) throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("tenant-id", String.valueOf(tenant))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(body)));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return JsonUtils.parseObject(response.body(), new TypeReference<>() {});
    }

    private Map<String, Object> expectCode(Map<String, Object> result, int code) {
        assertEquals(code, ((Number) result.get("code")).intValue(), String.valueOf(result.get("msg")));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> result) {
        expectCode(result, 0);
        return (Map<String, Object>) result.get("data");
    }

    private String login(String domain, String account) throws Exception {
        String endpoint = domain.equals("member") ? "/app-api/member/auth/login" : "/admin-api/system/auth/login";
        String accountField = domain.equals("member") ? "mobile" : "username";
        var result = data(api("POST", endpoint, Map.of(accountField, account, "password", System.getenv("JSS_TEST_HTTP_PASSWORD")), null, 1));
        String token = (String) result.get("accessToken");
        assertNotNull(token);
        tokens.add(Map.entry(domain, token));
        return token;
    }

    @AfterEach
    void logoutOnlyFixtureTokens() {
        for (var token : tokens) {
            try {
                api("POST", token.getKey().equals("member") ? "/app-api/jss/auth/logout" : "/admin-api/system/auth/logout", null, token.getValue(), 1);
            } catch (Exception ignored) {
                // The wrapper also stops the owned Redis and drops this schema after the JVM exits.
            }
        }
    }

    @Test
    void realMemberAndAdminLoginsKeepTokenDomainsAndDoNotAutoBindMatchingIdentities() throws Exception {
        expectCode(api("GET", "/app-api/product/category/list", null, null, 1), 0);
        expectCode(api("GET", "/app-api/jss/auth/context", null, null, 1), 401);
        expectCode(api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, "test1", 1), 401);
        String member = login("member", "13800009001");
        assertEquals(23001L, ((Number) data(api("GET", "/app-api/member/user/get", null, member, 1)).get("id")).longValue());
        var context = data(api("GET", "/app-api/jss/auth/context", null, member, 1));
        assertEquals("1", context.get("tenantId"));
        assertEquals("23001", context.get("memberUserId"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) context.get("capabilities")).get("workspace"));
        expectCode(api("POST", "/app-api/jss/auth/workspace-session", null, member, 1), 403);
        expectCode(api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, member, 1), 403);
        String reviewer = login("admin", "jssitreviewer");
        expectCode(api("GET", "/admin-api/system/auth/get-permission-info", null, reviewer, 1), 0);
        expectCode(api("POST", "/app-api/jss/auth/logout", null, member, 1), 0);
        expectCode(api("GET", "/app-api/member/user/get", null, member, 1), 401);
        expectCode(api("GET", "/admin-api/system/auth/get-permission-info", null, reviewer, 1), 0);
    }
}
