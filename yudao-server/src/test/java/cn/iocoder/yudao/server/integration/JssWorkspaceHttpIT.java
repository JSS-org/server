package cn.iocoder.yudao.server.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.module.jss.framework.JssProperties;
import cn.iocoder.yudao.server.YudaoServerApplication;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.sql.DataSource;

/** Full production server/security chain, with real HTTP/MySQL/Redis and disposable identities. */
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
            "spring.boot.admin.client.enabled=false",
            "xxl.job.enabled=false",
            "yudao.captcha.enable=false",
            "yudao.security.mock-enable=false",
            "jss.workspace.enabled=true",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=16379",
            "spring.data.redis.database=14",
            "spring.data.redis.password=${JSS_TEST_REDIS_PASSWORD}",
            "logging.file.name=${JSS_TEST_RUNTIME_DIR}/server.log",
            "logging.file.path=${JSS_TEST_RUNTIME_DIR}",
            "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
        })
class JssWorkspaceHttpIT {
    @Autowired private Environment environment;
    @Autowired private DataSource datasource;
    @Autowired private StringRedisTemplate redis;
    @Autowired private SecurityProperties security;
    @Autowired private JssProperties workspace;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<Map.Entry<String, String>> tokens = new ArrayList<>();

    @TestFactory
    Stream<DynamicTest> actualBrowserAcceptance() {
        if (!"1".equals(System.getenv("JSS_TEST_BROWSER"))) return Stream.empty();
        return Stream.of(
                DynamicTest.dynamicTest(
                        "memberEmployeeNavigationAndRevocationInRealBrowser",
                        () -> {
                            var frontend =
                                    Path.of(System.getenv("JSS_TEST_FRONTEND_DIR")).toRealPath();
                            assertTrue(
                                    Files.isRegularFile(
                                            frontend.resolve(
                                                    "playwright.authenticated.config.ts")));
                            var log =
                                    Path.of(
                                            System.getenv("JSS_TEST_RUNTIME_DIR"),
                                            "authenticated-browser.log");
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
                                                    "test:authenticated")
                                            : new ProcessBuilder(
                                                    "corepack", "pnpm", "test:authenticated");
                            int port =
                                    environment.getRequiredProperty(
                                            "local.server.port", Integer.class);
                            builder.environment()
                                    .put("JSS_TEST_HTTP_API_URL", "http://127.0.0.1:" + port);
                            builder.directory(frontend.toFile())
                                    .redirectErrorStream(true)
                                    .redirectOutput(log.toFile());
                            var process = builder.start();
                            try {
                                assertTrue(
                                        process.waitFor(240, TimeUnit.SECONDS),
                                        "Real browser acceptance timed out");
                                assertEquals(
                                        0,
                                        process.exitValue(),
                                        "Real browser acceptance failed; inspect the ignored authenticated-browser.log");
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

    @BeforeEach
    void assertRealOwnedInfrastructure() throws Exception {
        assertFalse(security.getMockEnable());
        assertTrue(workspace.isEnabled());
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

    private Map<String, Object> expectCode(Map<String, Object> result, int code) {
        assertEquals(
                code, ((Number) result.get("code")).intValue(), String.valueOf(result.get("msg")));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> result) {
        expectCode(result, 0);
        return (Map<String, Object>) result.get("data");
    }

    private String login(String domain, String account) throws Exception {
        String endpoint =
                domain.equals("member")
                        ? "/app-api/member/auth/login"
                        : "/admin-api/system/auth/login";
        String accountField = domain.equals("member") ? "mobile" : "username";
        var result =
                data(
                        api(
                                "POST",
                                endpoint,
                                Map.of(
                                        accountField,
                                        account,
                                        "password",
                                        System.getenv("JSS_TEST_HTTP_PASSWORD")),
                                null,
                                1));
        String token = (String) result.get("accessToken");
        assertNotNull(token);
        tokens.add(Map.entry(domain, token));
        return token;
    }

    @AfterEach
    void logoutOnlyFixtureTokens() {
        for (var token : tokens) {
            try {
                api(
                        "POST",
                        token.getKey().equals("member")
                                ? "/app-api/jss/auth/logout"
                                : "/admin-api/system/auth/logout",
                        null,
                        token.getValue(),
                        1);
            } catch (Exception ignored) {
                // The wrapper also stops the owned Redis and drops this schema after the JVM exits.
            }
        }
    }

    @Test
    void realMemberAndAdminLoginsKeepTokenDomainsAndDoNotAutoBindMatchingIdentities()
            throws Exception {
        expectCode(api("GET", "/app-api/product/category/list", null, null, 1), 0);
        expectCode(api("GET", "/app-api/jss/auth/context", null, null, 1), 401);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, "test1", 1),
                401);
        String member = login("member", "13800009001");
        assertEquals(
                23001L,
                ((Number) data(api("GET", "/app-api/member/user/get", null, member, 1)).get("id"))
                        .longValue());
        var context = data(api("GET", "/app-api/jss/auth/context", null, member, 1));
        assertEquals("1", context.get("tenantId"));
        assertEquals("23001", context.get("memberUserId"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) context.get("capabilities")).get("workspace"));
        expectCode(api("POST", "/app-api/jss/auth/workspace-session", null, member, 1), 403);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, member, 1),
                403);
        String reviewer = login("admin", "jssitreviewer");
        expectCode(api("GET", "/admin-api/system/auth/get-permission-info", null, reviewer, 1), 0);
        expectCode(api("POST", "/app-api/jss/auth/logout", null, member, 1), 0);
        expectCode(api("GET", "/app-api/member/user/get", null, member, 1), 401);
        expectCode(api("GET", "/admin-api/system/auth/get-permission-info", null, reviewer, 1), 0);
    }

    private String issueWorkspace(String member) throws Exception {
        long before = System.currentTimeMillis();
        var session = data(api("POST", "/app-api/jss/auth/workspace-session", null, member, 1));
        assertFalse(session.containsKey("refreshToken"));
        long expiry = ((Number) session.get("expiresTime")).longValue();
        assertTrue(expiry > before && expiry <= System.currentTimeMillis() + 300_000);
        String token = (String) session.get("accessToken");
        assertTrue(token.startsWith("jssw_"));
        return token;
    }

    @Test
    void reviewedBindingReplacementRevocationAndParentLogoutApplyToRealAdminRequests()
            throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String member = login("member", "13800009003");
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23003,
                                "adminUserId",
                                33001,
                                "reason",
                                "Reject foreign tenant staff"),
                        reviewer,
                        1),
                400);
        var binding =
                expectCode(
                        api(
                                "POST",
                                "/admin-api/jss/identity/bind",
                                Map.of(
                                        "memberUserId",
                                        23003,
                                        "adminUserId",
                                        23001,
                                        "reason",
                                        "Reviewed HTTP integration identity"),
                                reviewer,
                                1),
                        0);
        long identity = Long.parseLong(binding.get("data").toString());
        var context = data(api("GET", "/app-api/jss/auth/context", null, member, 1));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) context.get("capabilities")).get("workspace"));
        String first = issueWorkspace(member);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, first, 1), 0);
        expectCode(api("GET", "/app-api/member/user/get", null, first, 1), 403);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, first, 91001),
                401); // The token filter treats extension validation failures as unauthenticated.
        String replacement = issueWorkspace(member);
        assertFalse(first.equals(replacement));
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, first, 1),
                401);
        expectCode(
                api(
                        "GET",
                        "/admin-api/system/user/page?pageNo=1&pageSize=10",
                        null,
                        replacement,
                        1),
                0);
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/revoke",
                        Map.of("id", identity, "reason", "Reviewed HTTP revoke"),
                        reviewer,
                        1),
                0);
        expectCode(
                api(
                        "GET",
                        "/admin-api/system/user/page?pageNo=1&pageSize=10",
                        null,
                        replacement,
                        1),
                401);
        context = data(api("GET", "/app-api/jss/auth/context", null, member, 1));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) context.get("capabilities")).get("workspace"));
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23003,
                                "adminUserId",
                                23001,
                                "reason",
                                "Reviewed HTTP rebind"),
                        reviewer,
                        1),
                0);
        expectCode(
                api(
                        "GET",
                        "/admin-api/system/user/page?pageNo=1&pageSize=10",
                        null,
                        replacement,
                        1),
                401);
        String current = issueWorkspace(member);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, current, 1),
                0);
        expectCode(api("POST", "/app-api/jss/auth/logout", null, member, 1), 0);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, current, 1),
                401);
        expectCode(api("GET", "/admin-api/system/auth/get-permission-info", null, reviewer, 1), 0);
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/revoke",
                        Map.of("id", identity, "reason", "Finish HTTP fixture"),
                        reviewer,
                        1),
                0);
        try (var connection = datasource.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT action,binding_version,operator_id,tenant_id FROM jss_identity_audit WHERE member_user_id=23003 ORDER BY id")) {
            try (var rows = statement.executeQuery()) {
                String[] actions = {"BIND", "REVOKE", "BIND", "REVOKE"};
                for (int i = 0; i < actions.length; i++) {
                    assertTrue(rows.next());
                    assertEquals(actions[i], rows.getString(1));
                    assertEquals(i + 1, rows.getLong(2));
                    assertEquals(1L, rows.getLong(3));
                    assertEquals(1L, rows.getLong(4));
                }
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void workspaceEntryPermissionDoesNotGrantUserQueryOrIdentityReview() throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String member = login("member", "13800009002");
        var binding =
                expectCode(
                        api(
                                "POST",
                                "/admin-api/jss/identity/bind",
                                Map.of(
                                        "memberUserId",
                                        23002,
                                        "adminUserId",
                                        23002,
                                        "reason",
                                        "Review entry-only staff"),
                                reviewer,
                                1),
                        0);
        String token = issueWorkspace(member);
        expectCode(api("GET", "/admin-api/system/auth/get-permission-info", null, token, 1), 0);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, token, 1),
                403);
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23001,
                                "adminUserId",
                                23001,
                                "reason",
                                "Entry cannot review identities"),
                        token,
                        1),
                403);
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/revoke",
                        Map.of(
                                "id",
                                binding.get("data"),
                                "reason",
                                "Finish entry-only HTTP fixture"),
                        reviewer,
                        1),
                0);
        expectCode(api("GET", "/admin-api/system/auth/get-permission-info", null, token, 1), 401);
    }

    private long menuId(String permission) throws Exception {
        try (var connection = datasource.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT id FROM system_menu WHERE permission=? AND deleted=b'0'")) {
            statement.setString(1, permission);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                long id = rows.getLong(1);
                assertFalse(rows.next());
                return id;
            }
        }
    }

    private void assignMenus(String reviewer, List<Long> menus) throws Exception {
        var expanded = new LinkedHashSet<>(menus);
        try (var connection = datasource.getConnection()) {
            for (long menu : menus) expanded.addAll(JssHttpFixtures.menuBranch(connection, menu));
        }
        expectCode(
                api(
                        "POST",
                        "/admin-api/system/permission/assign-role-menu",
                        Map.of("roleId", 23004, "menuIds", expanded),
                        reviewer,
                        1),
                0);
    }

    private void updateMutableRole(String reviewer, int status) throws Exception {
        expectCode(
                api(
                        "PUT",
                        "/admin-api/system/role/update",
                        Map.of(
                                "id",
                                23004,
                                "name",
                                "HTTP mutable role",
                                "code",
                                "jss_http_mutable",
                                "sort",
                                4,
                                "status",
                                status),
                        reviewer,
                        1),
                0);
    }

    private void assertCapability(String member, boolean allowed) throws Exception {
        var context = data(api("GET", "/app-api/jss/auth/context", null, member, 1));
        assertEquals(allowed, ((Map<?, ?>) context.get("capabilities")).get("workspace"));
        expectCode(api("GET", "/app-api/member/user/get", null, member, 1), 0);
    }

    @Test
    void liveRoleMenusRoleStatusAndUserRoleAssignmentsRevokeExistingWorkspaceAccess()
            throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String member = login("member", "13800009004");
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23004,
                                "adminUserId",
                                23004,
                                "reason",
                                "Review mutable RBAC fixture"),
                        reviewer,
                        1),
                0);
        long entry = menuId("jss:workspace:access");
        long query = menuId("system:user:query");
        String original = issueWorkspace(member);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, original, 1),
                0);
        assignMenus(reviewer, List.of(entry));
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, original, 1),
                403);
        assertCapability(member, true);
        var permissionInfo =
                data(api("GET", "/admin-api/system/auth/get-permission-info", null, original, 1));
        assertFalse(((List<?>) permissionInfo.get("permissions")).contains("system:user:query"));
        assignMenus(reviewer, List.of());
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, original, 1),
                401);
        assertCapability(member, false);
        expectCode(api("POST", "/app-api/jss/auth/workspace-session", null, member, 1), 403);
        assignMenus(reviewer, List.of(entry, query));
        assertCapability(member, true);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, original, 1),
                401);
        String enabled = issueWorkspace(member);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, enabled, 1),
                0);
        updateMutableRole(reviewer, 1);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, enabled, 1),
                401);
        assertCapability(member, false);
        updateMutableRole(reviewer, 0);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, enabled, 1),
                401);
        String assigned = issueWorkspace(member);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, assigned, 1),
                0);
        expectCode(
                api(
                        "POST",
                        "/admin-api/system/permission/assign-user-role",
                        Map.of("userId", 23004, "roleIds", List.of()),
                        reviewer,
                        1),
                0);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, assigned, 1),
                401);
        assertCapability(member, false);
        expectCode(
                api(
                        "POST",
                        "/admin-api/system/permission/assign-user-role",
                        Map.of("userId", 23004, "roleIds", List.of(23004)),
                        reviewer,
                        1),
                0);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, assigned, 1),
                401);
        expectCode(
                api(
                        "GET",
                        "/admin-api/system/user/page?pageNo=1&pageSize=10",
                        null,
                        issueWorkspace(member),
                        1),
                0);
    }

    @Test
    void disablingBoundEmployeeInvalidatesWorkspaceWithoutLoggingOutTheMember() throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String member = login("member", "13800009005");
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23005,
                                "adminUserId",
                                23005,
                                "reason",
                                "Review employee status fixture"),
                        reviewer,
                        1),
                0);
        String token = issueWorkspace(member);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, token, 1), 0);
        expectCode(
                api(
                        "PUT",
                        "/admin-api/system/user/update-status",
                        Map.of("id", 23005, "status", 1),
                        reviewer,
                        1),
                0);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, token, 1),
                401);
        assertCapability(member, false);
        expectCode(api("POST", "/app-api/jss/auth/workspace-session", null, member, 1), 403);
        expectCode(
                api(
                        "PUT",
                        "/admin-api/system/user/update-status",
                        Map.of("id", 23005, "status", 0),
                        reviewer,
                        1),
                0);
        assertCapability(member, true);
        expectCode(
                api("GET", "/admin-api/system/user/page?pageNo=1&pageSize=10", null, token, 1),
                401);
        expectCode(
                api(
                        "GET",
                        "/admin-api/system/user/page?pageNo=1&pageSize=10",
                        null,
                        issueWorkspace(member),
                        1),
                0);
    }

    private Map<String, Object> menuConfiguration(String reviewer, long menu) throws Exception {
        var configuration =
                new HashMap<>(
                        data(
                                api(
                                        "GET",
                                        "/admin-api/system/menu/get?id=" + menu,
                                        null,
                                        reviewer,
                                        1)));
        configuration
                .keySet()
                .retainAll(
                        List.of(
                                "id",
                                "name",
                                "permission",
                                "type",
                                "sort",
                                "parentId",
                                "path",
                                "icon",
                                "component",
                                "componentName",
                                "status",
                                "visible",
                                "keepAlive",
                                "alwaysShow"));
        return configuration;
    }

    private void saveMenu(String reviewer, Map<String, Object> configuration, int status)
            throws Exception {
        var update = new HashMap<>(configuration);
        update.put("status", status);
        expectCode(api("PUT", "/admin-api/system/menu/update", update, reviewer, 1), 0);
    }

    @Test
    void disabledButtonsParentsAndWorkspaceEntryRestrictOnlyTheApplicationScopedToken()
            throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String nativeStaff = login("admin", "jssitmenu");
        String member = login("member", "13800009008");
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23008,
                                "adminUserId",
                                23008,
                                "reason",
                                "Review menu suspension fixture"),
                        reviewer,
                        1),
                0);
        String token = issueWorkspace(member);
        String page = "/admin-api/system/user/page?pageNo=1&pageSize=10";
        expectCode(api("GET", page, null, token, 1), 0);
        var query = menuConfiguration(reviewer, menuId("system:user:query"));
        try {
            saveMenu(reviewer, query, 1);
            expectCode(api("GET", page, null, token, 1), 403);
            assertCapability(member, true);
            // Preserve the pre-existing native OAuth/RBAC semantics.
            expectCode(api("GET", page, null, nativeStaff, 1), 0);
        } finally {
            saveMenu(reviewer, query, 0);
        }
        expectCode(api("GET", page, null, token, 1), 0);
        var parent = menuConfiguration(reviewer, Long.parseLong(query.get("parentId").toString()));
        try {
            saveMenu(reviewer, parent, 1);
            expectCode(api("GET", page, null, token, 1), 403);
            assertCapability(member, true);
        } finally {
            saveMenu(reviewer, parent, 0);
        }
        expectCode(api("GET", page, null, token, 1), 0);
        var entry = menuConfiguration(reviewer, menuId("jss:workspace:access"));
        try {
            saveMenu(reviewer, entry, 1);
            assertCapability(member, false);
            expectCode(api("POST", "/app-api/jss/auth/workspace-session", null, member, 1), 403);
            expectCode(api("GET", page, null, token, 1), 401);
            expectCode(api("GET", page, null, nativeStaff, 1), 0);
        } finally {
            saveMenu(reviewer, entry, 0);
        }
        expectCode(api("GET", page, null, token, 1), 401);
        expectCode(api("GET", page, null, issueWorkspace(member), 1), 0);
    }
}
