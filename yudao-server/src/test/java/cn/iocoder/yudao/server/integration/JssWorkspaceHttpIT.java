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
        classes = {YudaoServerApplication.class, JssWorkspaceHttpIT.ApiLogProbe.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=local,jss-http-fixture",
            "server.address=127.0.0.1",
            "spring.quartz.auto-startup=false",
            "spring.boot.admin.client.enabled=false",
            "xxl.job.enabled=false",
            "yudao.captcha.enable=false",
            "yudao.security.mock-enable=false",
            "jss.workspace.enabled=true",
            "yudao.access-log.enable=true",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=16379",
            "spring.data.redis.database=14",
            "spring.data.redis.password=${JSS_TEST_REDIS_PASSWORD}",
            "logging.file.name=${JSS_TEST_RUNTIME_DIR}/server.log",
            "logging.file.path=${JSS_TEST_RUNTIME_DIR}",
            "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
        })
class JssWorkspaceHttpIT {
    /** Only compiled into tests and activated in this guarded disposable HTTP context. */
    @org.springframework.web.bind.annotation.RestController
    @org.springframework.context.annotation.Profile("jss-http-fixture")
    public static class ApiLogProbe {
        @org.springframework.web.bind.annotation.GetMapping(
                "/admin-api/infra/jss-it/api-log-failure")
        @org.springframework.security.access.prepost.PreAuthorize(
                "@ss.hasPermission('infra:api-error-log:query')")
        public Object fail(
                @org.springframework.web.bind.annotation.RequestParam("marker") String marker) {
            throw new IllegalStateException("JSS isolated API log probe: " + marker);
        }
    }

    @Autowired private Environment environment;
    @Autowired private DataSource datasource;
    @Autowired private StringRedisTemplate redis;
    @Autowired private SecurityProperties security;
    @Autowired private JssProperties workspace;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private record OwnedToken(String domain, String token, long tenant) {}

    private final List<OwnedToken> tokens = new ArrayList<>();

    @TestFactory
    Stream<DynamicTest> actualCrmBpmAcceptance() {
        if (!"1".equals(System.getenv("JSS_TEST_CRM_BPM"))) return Stream.empty();
        return Stream.of(DynamicTest.dynamicTest("nativeCrmAndBpmModulesUseManagedSchemaAndRealHttp", () -> {
            assertEquals("false", environment.getProperty("flowable.database-schema-update"));
            String token = login("admin", "jssitreviewer");
            var category = expectCode(api("POST", "/admin-api/bpm/category/create",
                    Map.of("name", "Owned BPM category", "code", "jss_owned", "status", 0, "sort", 1), token, 1), 0).get("data").toString();
            assertEquals("Owned BPM category", data(api("GET", "/admin-api/bpm/category/get?id=" + category, null, token, 1)).get("name"));
            var group = expectCode(api("POST", "/admin-api/bpm/user-group/create",
                    Map.of("name", "Owned BPM group", "description", "Native JSON roundtrip", "status", 0, "userIds", List.of(1)), token, 1), 0).get("data").toString();
            var savedGroup = data(api("GET", "/admin-api/bpm/user-group/get?id=" + group, null, token, 1));
            assertEquals("Owned BPM group", savedGroup.get("name"));
            assertEquals(List.of("1"), ((List<?>) savedGroup.get("userIds")).stream().map(Object::toString).toList());
            var customer = expectCode(api("POST", "/admin-api/crm/customer/create",
                    Map.of("name", "Owned CRM customer", "ownerUserId", 1, "remark", "Native owner and permissions"), token, 1), 0).get("data").toString();
            var savedCustomer = data(api("GET", "/admin-api/crm/customer/get?id=" + customer, null, token, 1));
            assertEquals("Owned CRM customer", savedCustomer.get("name"));
            assertEquals("1", savedCustomer.get("ownerUserId").toString());
            var definitions = expectCode(api("GET", "/admin-api/bpm/process-definition/list?suspensionState=1", null, token, 1), 0);
            assertTrue(definitions.get("data") instanceof List<?>);
            var processes = data(api("GET", "/admin-api/bpm/process-instance/my-page?pageNo=1&pageSize=10", null, token, 1));
            assertNotNull(processes.get("list"));
            // A member sharing the super-admin's numeric ID must not inherit that role.
            try (var connection = datasource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO member_user (id,tenant_id,mobile,password,nickname,status,register_ip,group_id) SELECT 1,1,'13900009714',password,'Owned ID collision',0,'127.0.0.1',23099 FROM member_user WHERE id=23001");
            }
            String member = login("member", "13900009714");
            assertEquals("1", data(api("GET", "/app-api/member/user/get", null, member, 1)).get("id").toString());
            var context = data(api("GET", "/app-api/jss/auth/context", null, member, 1));
            assertEquals(Boolean.FALSE, ((Map<?,?>) context.get("capabilities")).get("workspace"));
            expectCode(api("GET", "/admin-api/bpm/category/page?pageNo=1&pageSize=10", null, member, 1), 403);
            expectCode(api("GET", "/admin-api/bpm/category/page?pageNo=1&pageSize=10", null, null, 1), 401);
        }));
    }

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
                                        process.waitFor(1080, TimeUnit.SECONDS),
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
        return api(method, path, body, token, tenant, Map.of());
    }

    private Map<String, Object> api(
            String method,
            String path,
            Object body,
            String token,
            long tenant,
            Map<String, String> headers)
            throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(15))
                        .header("tenant-id", String.valueOf(tenant))
                        .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        headers.forEach(request::header);
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
        return login(domain, account, 1);
    }

    private String login(String domain, String account, long tenant) throws Exception {
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
                                tenant));
        String token = (String) result.get("accessToken");
        assertNotNull(token);
        tokens.add(new OwnedToken(domain, token, tenant));
        return token;
    }

    @AfterEach
    void logoutOnlyFixtureTokens() {
        for (var token : tokens) {
            try {
                api(
                        "POST",
                        token.domain().equals("member")
                                ? "/app-api/jss/auth/logout"
                                : "/admin-api/system/auth/logout",
                        null,
                        token.token(),
                        token.tenant());
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
        try (var connection = datasource.getConnection();
                var statement = connection.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT g.name FROM member_user u JOIN member_group g ON g.id=u.group_id AND g.tenant_id=u.tenant_id WHERE u.id=23001 AND u.tenant_id=1")) {
            assertTrue(rows.next());
            assertEquals("员工", rows.getString(1));
        }
        assertEquals(
                23001L,
                ((Number) data(api("GET", "/app-api/member/user/get", null, member, 1)).get("id"))
                        .longValue());
        var context = data(api("GET", "/app-api/jss/auth/context", null, member, 1));
        assertEquals("1", context.get("tenantId"));
        assertEquals("23001", context.get("memberUserId"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) context.get("capabilities")).get("workspace"));
        expectCode(api("POST", "/app-api/jss/auth/workspace-session", null, member, 1), 403);
        var forged =
                data(
                        api(
                                "GET",
                                "/app-api/jss/auth/context?adminUserId=1&workspace=true",
                                null,
                                member,
                                1));
        assertEquals("23001", forged.get("memberUserId"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) forged.get("capabilities")).get("workspace"));
        expectCode(
                api(
                        "POST",
                        "/app-api/jss/auth/workspace-session",
                        Map.of(
                                "adminUserId",
                                1,
                                "workspace",
                                true,
                                "permissions",
                                List.of("*:*:*")),
                        member,
                        1),
                403);
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
        return issueWorkspace(member, 1);
    }

    private String issueWorkspace(String member, long tenant) throws Exception {
        long before = System.currentTimeMillis();
        var session =
                data(api("POST", "/app-api/jss/auth/workspace-session", null, member, tenant));
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
        assignMenus(reviewer, 23004, menus);
    }

    private void assignMenus(String reviewer, long role, List<Long> menus) throws Exception {
        var expanded = new LinkedHashSet<>(menus);
        try (var connection = datasource.getConnection()) {
            for (long menu : menus) expanded.addAll(JssHttpFixtures.menuBranch(connection, menu));
        }
        expectCode(
                api(
                        "POST",
                        "/admin-api/system/permission/assign-role-menu",
                        Map.of("roleId", role, "menuIds", expanded),
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
    void disabledBranchesRestrictBothTokensWhileWorkspaceEntryRestrictsOnlyApplicationToken()
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
        expectCode(api("GET", page, null, nativeStaff, 1), 0);
        var query = menuConfiguration(reviewer, menuId("system:user:query"));
        try {
            saveMenu(reviewer, query, 1);
            expectCode(api("GET", page, null, token, 1), 403);
            assertCapability(member, true);
            // Shared menu hierarchy enforcement also applies to native OAuth RBAC.
            expectCode(api("GET", page, null, nativeStaff, 1), 403);
        } finally {
            saveMenu(reviewer, query, 0);
        }
        expectCode(api("GET", page, null, token, 1), 0);
        expectCode(api("GET", page, null, nativeStaff, 1), 0);
        var parent = menuConfiguration(reviewer, Long.parseLong(query.get("parentId").toString()));
        try {
            saveMenu(reviewer, parent, 1);
            expectCode(api("GET", page, null, token, 1), 403);
            expectCode(api("GET", page, null, nativeStaff, 1), 403);
            assertCapability(member, true);
        } finally {
            saveMenu(reviewer, parent, 0);
        }
        expectCode(api("GET", page, null, token, 1), 0);
        expectCode(api("GET", page, null, nativeStaff, 1), 0);
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

    private void saveTenant(
            String reviewer, Map<String, Object> original, int status, Object expiry)
            throws Exception {
        var configuration = new HashMap<>(original);
        configuration
                .keySet()
                .retainAll(
                        List.of(
                                "id",
                                "name",
                                "contactName",
                                "contactMobile",
                                "status",
                                "websites",
                                "packageId",
                                "expireTime",
                                "accountCount"));
        configuration.put("status", status);
        configuration.put("expireTime", expiry);
        expectCode(api("PUT", "/admin-api/system/tenant/update", configuration, reviewer, 1), 0);
    }

    @Test
    void suspendedAndExpiredTenantRejectWorkspaceImmediatelyDespiteAWarmedFrameworkCache()
            throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String foreignReviewer = login("admin", "jssitforeign", 91001);
        String member = login("member", "13800009101", 91001);
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                33001,
                                "adminUserId",
                                33001,
                                "reason",
                                "Review tenant lifecycle fixture"),
                        foreignReviewer,
                        91001),
                0);
        var original = data(api("GET", "/admin-api/system/tenant/get?id=91001", null, reviewer, 1));
        String page = "/admin-api/system/user/page?pageNo=1&pageSize=10";
        String token = issueWorkspace(member, 91001);
        var tenantPage = data(api("GET", page, null, token, 91001));
        assertEquals(1, ((Number) tenantPage.get("total")).intValue());
        var rows = (List<?>) tenantPage.get("list");
        assertEquals("33001", ((Map<?, ?>) rows.getFirst()).get("id").toString());
        try {
            saveTenant(reviewer, original, 1, original.get("expireTime"));
            expectCode(api("GET", page, null, token, 91001), 401);
            expectCode(
                    api("GET", "/app-api/jss/auth/context", null, member, 91001),
                    cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_DISABLE
                            .getCode());
        } finally {
            saveTenant(reviewer, original, 0, original.get("expireTime"));
        }
        expectCode(api("GET", page, null, token, 91001), 401);
        String renewed = issueWorkspace(member, 91001);
        expectCode(api("GET", page, null, renewed, 91001), 0);
        try {
            saveTenant(reviewer, original, 0, System.currentTimeMillis() - 60_000);
            expectCode(api("GET", page, null, renewed, 91001), 401);
            expectCode(
                    api("GET", "/app-api/jss/auth/context", null, member, 91001),
                    cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_EXPIRE
                            .getCode());
            expectCode(
                    api("POST", "/app-api/jss/auth/workspace-session", null, member, 91001),
                    cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_EXPIRE
                            .getCode());
        } finally {
            saveTenant(reviewer, original, 0, original.get("expireTime"));
        }
        expectCode(api("GET", page, null, renewed, 91001), 401);
        expectCode(api("GET", page, null, issueWorkspace(member, 91001), 91001), 0);
        expectCode(api("GET", "/app-api/member/user/get", null, member, 91001), 0);
    }

    private void setRoleStatus(String reviewer, long role, int status) throws Exception {
        var configuration =
                new HashMap<>(
                        data(
                                api(
                                        "GET",
                                        "/admin-api/system/role/get?id=" + role,
                                        null,
                                        reviewer,
                                        1)));
        configuration.keySet().retainAll(List.of("id", "name", "code", "sort", "status", "remark"));
        configuration.put("status", status);
        expectCode(api("PUT", "/admin-api/system/role/update", configuration, reviewer, 1), 0);
    }

    @Test
    void enabledRolesCombineButDisabledRolesContributeNeitherEntryNorBusinessAccess()
            throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String member = login("member", "13800009009");
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23009,
                                "adminUserId",
                                23009,
                                "reason",
                                "Review multiple role fixture"),
                        reviewer,
                        1),
                0);
        String page = "/admin-api/system/user/page?pageNo=1&pageSize=10";
        try {
            assertCapability(member, true);
            String entryOnly = issueWorkspace(member);
            expectCode(api("GET", page, null, entryOnly, 1), 403);
            setRoleStatus(reviewer, 23009, 1);
            assertCapability(member, false);
            expectCode(api("GET", page, null, entryOnly, 1), 401);
            expectCode(api("POST", "/app-api/jss/auth/workspace-session", null, member, 1), 403);
            setRoleStatus(reviewer, 23010, 0);
            assertCapability(member, true);
            expectCode(api("GET", page, null, entryOnly, 1), 401);
            String full = issueWorkspace(member);
            expectCode(api("GET", page, null, full, 1), 0);
            setRoleStatus(reviewer, 23009, 0);
            setRoleStatus(reviewer, 23010, 1);
            assertCapability(member, true);
            expectCode(api("GET", page, null, full, 1), 403);
        } finally {
            setRoleStatus(reviewer, 23009, 0);
            setRoleStatus(reviewer, 23010, 1);
        }
    }

    @Test
    void workspaceCannotUseVisitTenantHeaderEvenWhenItsNativeAdministratorCan() throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String member = login("member", "13800009010");
        assertCapability(member, false);
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23010,
                                "adminUserId",
                                1,
                                "reason",
                                "Review tenant-bound administrator fixture"),
                        reviewer,
                        1),
                0);
        String token = issueWorkspace(member);
        String page = "/admin-api/system/user/page?pageNo=1&pageSize=10";
        expectCode(api("GET", page, null, token, 1), 0);
        expectCode(api("GET", page, null, token, 1, Map.of("visit-tenant-id", "91001")), 403);
        var permittedNativeVisit =
                data(api("GET", page, null, reviewer, 1, Map.of("visit-tenant-id", "91001")));
        assertEquals(1, ((Number) permittedNativeVisit.get("total")).intValue());
        var rows = (List<?>) permittedNativeVisit.get("list");
        assertEquals("33001", ((Map<?, ?>) rows.getFirst()).get("id").toString());
        expectCode(api("GET", page, null, token, 1), 0);
    }

    @Autowired
    private cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender socketSender;

    private static class SocketProbe implements java.net.http.WebSocket.Listener {
        final java.util.concurrent.BlockingQueue<String> frames =
                new java.util.concurrent.LinkedBlockingQueue<>();
        final java.util.concurrent.CompletableFuture<Integer> closed =
                new java.util.concurrent.CompletableFuture<>();
        final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(java.net.http.WebSocket socket) {
            socket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(
                java.net.http.WebSocket socket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                frames.add(buffer.toString());
                buffer.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(
                java.net.http.WebSocket socket, int status, String reason) {
            closed.complete(status);
            return null;
        }
    }

    private java.net.http.WebSocket connectSocket(String token, long tenant, SocketProbe probe) {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        return http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .header("tenant-id", String.valueOf(tenant))
                .buildAsync(
                        URI.create("ws://127.0.0.1:" + port + "/infra/ws?token=" + token), probe)
                .join();
    }

    private void assertSocketFrame(SocketProbe probe, String type, String content)
            throws Exception {
        String frame = probe.frames.poll(5, TimeUnit.SECONDS);
        assertNotNull(frame, "Expected actual websocket frame: " + type);
        Map<String, Object> parsed = JsonUtils.parseObject(frame, new TypeReference<>() {});
        assertEquals(type, parsed.get("type"));
        assertTrue(parsed.get("content").toString().contains(content));
    }

    @Test
    void realWebSocketUsesWorkspaceCredentialLiveKefuPermissionsTenantAndParentRevocation()
            throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String member = login("member", "13800009011");
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23011,
                                "adminUserId",
                                23011,
                                "reason",
                                "Reviewed websocket fixture"),
                        reviewer,
                        1),
                0);
        long entry = menuId("jss:workspace:access"),
                conversation = menuId("promotion:kefu-conversation:query"),
                message = menuId("promotion:kefu-message:query");
        assignMenus(reviewer, 23011, List.of(entry));
        String token = issueWorkspace(member);
        var probe = new SocketProbe();
        var socket = connectSocket(token, 1, probe);
        try {
            socket.sendText("ping", true).join();
            assertEquals("pong", probe.frames.poll(5, TimeUnit.SECONDS));
            cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(
                    1L,
                    () ->
                            socketSender.send(
                                    2, "kefu_message_type", "{\"marker\":\"without-module\"}"));
            assertNull(probe.frames.poll(1, TimeUnit.SECONDS));
            assignMenus(reviewer, 23011, List.of(entry, conversation, message));
            cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(
                    1L,
                    () -> socketSender.send(2, "kefu_message_type", "{\"marker\":\"allowed\"}"));
            String frame = probe.frames.poll(5, TimeUnit.SECONDS);
            assertNotNull(frame);
            assertTrue(frame.contains("allowed"));
            cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(
                    91001L,
                    () ->
                            socketSender.send(
                                    2,
                                    23011L,
                                    "kefu_message_type",
                                    "{\"marker\":\"foreign-tenant\"}"));
            assertNull(probe.frames.poll(1, TimeUnit.SECONDS));
            // Actual original business controllers publish the same frames over real sockets.
            var memberProbe = new SocketProbe();
            var memberSocket = connectSocket(member, 1, memberProbe);
            try {
                expectCode(
                        api(
                                "POST",
                                "/app-api/promotion/kefu-message/send",
                                Map.of(
                                        "contentType",
                                        1,
                                        "content",
                                        "{\"text\":\"actual-member-kefu\"}",
                                        "senderId",
                                        33001,
                                        "senderType",
                                        2),
                                member,
                                1),
                        0);
                assertSocketFrame(probe, "kefu_message_type", "actual-member-kefu");
                assertSocketFrame(memberProbe, "kefu_message_type", "actual-member-kefu");
                var conversations =
                        (List<?>)
                                expectCode(
                                                api(
                                                        "GET",
                                                        "/admin-api/promotion/kefu-conversation/list",
                                                        null,
                                                        token,
                                                        1),
                                                0)
                                        .get("data");
                var conversationRow =
                        conversations.stream()
                                .map(row -> (Map<?, ?>) row)
                                .filter(row -> "23011".equals(row.get("userId").toString()))
                                .findFirst()
                                .orElseThrow();
                String conversationId = conversationRow.get("id").toString();
                var reply =
                        Map.of(
                                "conversationId",
                                conversationId,
                                "contentType",
                                1,
                                "content",
                                "{\"text\":\"actual-admin-kefu\"}");
                expectCode(
                        api("POST", "/admin-api/promotion/kefu-message/send", reply, token, 1),
                        403);
                expectCode(
                        api(
                                "PUT",
                                "/admin-api/promotion/kefu-message/update-read-status?conversationId="
                                        + conversationId,
                                null,
                                token,
                                1),
                        403);
                expectCode(
                        api(
                                "PUT",
                                "/admin-api/promotion/kefu-conversation/update-conversation-pinned",
                                Map.of("id", conversationId, "adminPinned", true),
                                token,
                                1),
                        403);
                expectCode(
                        api(
                                "DELETE",
                                "/admin-api/promotion/kefu-conversation/delete?id="
                                        + conversationId,
                                null,
                                token,
                                1),
                        403);
                assignMenus(
                        reviewer,
                        23011,
                        List.of(
                                entry,
                                conversation,
                                message,
                                menuId("promotion:kefu-message:send"),
                                menuId("promotion:kefu-message:update"),
                                menuId("promotion:kefu-conversation:update"),
                                menuId("promotion:kefu-conversation:delete")));
                expectCode(
                        api(
                                "PUT",
                                "/admin-api/promotion/kefu-message/update-read-status?conversationId="
                                        + conversationId,
                                null,
                                token,
                                1),
                        0);
                assertSocketFrame(probe, "kefu_message_read_status_change", conversationId);
                assertSocketFrame(memberProbe, "kefu_message_read_status_change", conversationId);
                expectCode(
                        api("POST", "/admin-api/promotion/kefu-message/send", reply, token, 1), 0);
                assertSocketFrame(probe, "kefu_message_type", "actual-admin-kefu");
                assertSocketFrame(memberProbe, "kefu_message_type", "actual-admin-kefu");
                var history =
                        (List<?>)
                                expectCode(
                                                api(
                                                        "GET",
                                                        "/admin-api/promotion/kefu-message/list?conversationId="
                                                                + conversationId,
                                                        null,
                                                        token,
                                                        1),
                                                0)
                                        .get("data");
                assertTrue(
                        history.stream()
                                .map(row -> (Map<?, ?>) row)
                                .anyMatch(
                                        row ->
                                                row.get("content")
                                                                .toString()
                                                                .contains("actual-member-kefu")
                                                        && "23011"
                                                                .equals(
                                                                        row.get("senderId")
                                                                                .toString())
                                                        && "1"
                                                                .equals(
                                                                        row.get("senderType")
                                                                                .toString())
                                                        && Boolean.TRUE.equals(
                                                                row.get("readStatus"))));
                expectCode(
                        api(
                                "PUT",
                                "/admin-api/promotion/kefu-conversation/update-conversation-pinned",
                                Map.of("id", conversationId, "adminPinned", true),
                                token,
                                1),
                        0);
                assertEquals(
                        Boolean.TRUE,
                        data(api(
                                        "GET",
                                        "/admin-api/promotion/kefu-conversation/get?id="
                                                + conversationId,
                                        null,
                                        token,
                                        1))
                                .get("adminPinned"));
                expectCode(
                        api(
                                "DELETE",
                                "/admin-api/promotion/kefu-conversation/delete?id="
                                        + conversationId,
                                null,
                                token,
                                1),
                        0);
                var remaining =
                        (List<?>)
                                expectCode(
                                                api(
                                                        "GET",
                                                        "/admin-api/promotion/kefu-conversation/list",
                                                        null,
                                                        token,
                                                        1),
                                                0)
                                        .get("data");
                assertTrue(
                        remaining.stream()
                                .map(row -> (Map<?, ?>) row)
                                .noneMatch(row -> conversationId.equals(row.get("id").toString())));
            } finally {
                memberSocket.abort();
            }
            assignMenus(reviewer, 23011, List.of(entry));
            cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(
                    1L,
                    () ->
                            socketSender.send(
                                    2,
                                    "kefu_message_read_status_change",
                                    "{\"marker\":\"revoked-module\"}"));
            assertNull(probe.frames.poll(1, TimeUnit.SECONDS));
            expectCode(api("POST", "/app-api/jss/auth/logout", null, member, 1), 0);
            socket.sendText("ping", true).join();
            assertEquals(1008, probe.closed.get(5, TimeUnit.SECONDS));
            assertNull(probe.frames.poll(1, TimeUnit.SECONDS));
        } finally {
            socket.abort();
            assignMenus(reviewer, 23011, List.of(entry));
        }
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> connectSocket("jssw_invalid", 1, new SocketProbe()));
    }

    @Test
    void realNotifyManagementAndPersonalReadKeepPermissionsAndRecipientDomainsSeparate()
            throws Exception {
        String reviewer = login("admin", "jssitreviewer");
        String member = login("member", "13800009013");
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23013,
                                "adminUserId",
                                23013,
                                "reason",
                                "Reviewed owned notify fixture"),
                        reviewer,
                        1),
                0);
        List<Long> queries =
                List.of(
                        menuId("jss:workspace:access"),
                        menuId("system:notify-template:query"),
                        menuId("system:notify-message:query"));
        assignMenus(reviewer, 23013, queries);
        String token = issueWorkspace(member);
        var template =
                new HashMap<String, Object>(
                        Map.of(
                                "name",
                                "Owned notify template",
                                "code",
                                "jss_owned_notify_fixture",
                                "nickname",
                                "Owned sender",
                                "type",
                                1,
                                "status",
                                0,
                                "content",
                                "Before {name}",
                                "remark",
                                "Before"));
        var send =
                new HashMap<String, Object>(
                        Map.of(
                                "userId",
                                23013,
                                "userType",
                                2,
                                "templateCode",
                                "jss_owned_notify_fixture",
                                "templateParams",
                                Map.of("name", "Workspace")));
        expectCode(
                api("POST", "/admin-api/system/notify-template/create", template, token, 1), 403);
        var deniedUpdate = new HashMap<>(template);
        deniedUpdate.put("id", 1);
        expectCode(
                api("PUT", "/admin-api/system/notify-template/update", deniedUpdate, token, 1),
                403);
        expectCode(
                api("DELETE", "/admin-api/system/notify-template/delete?id=1", null, token, 1),
                403);
        expectCode(
                api("POST", "/admin-api/system/notify-template/send-notify", send, token, 1), 403);
        var granted = new ArrayList<>(queries);
        for (String action : List.of("create", "update", "delete"))
            granted.add(menuId("system:notify-template:" + action));
        assignMenus(reviewer, 23013, granted);
        var created = api("POST", "/admin-api/system/notify-template/create", template, token, 1);
        expectCode(created, 0);
        String templateId = created.get("data").toString();
        template.put("id", templateId);
        template.put("content", "Hello {name}");
        template.put("remark", "");
        expectCode(api("PUT", "/admin-api/system/notify-template/update", template, token, 1), 0);
        var loaded =
                data(
                        api(
                                "GET",
                                "/admin-api/system/notify-template/get?id=" + templateId,
                                null,
                                token,
                                1));
        assertEquals(List.of("name"), loaded.get("params"));
        assertEquals("", loaded.get("remark"));
        expectCode(
                api("POST", "/admin-api/system/notify-template/send-notify", send, token, 1), 403);
        granted.add(menuId("system:notify-template:send-notify"));
        assignMenus(reviewer, 23013, granted);
        var missingParams = new HashMap<>(send);
        missingParams.put("templateParams", Map.of());
        assertNotEquals(
                0,
                ((Number)
                                api(
                                                "POST",
                                                "/admin-api/system/notify-template/send-notify",
                                                missingParams,
                                                token,
                                                1)
                                        .get("code"))
                        .intValue());
        var delivered =
                api("POST", "/admin-api/system/notify-template/send-notify", send, token, 1);
        expectCode(delivered, 0);
        String adminMessage = delivered.get("data").toString();
        send.put("userType", 1);
        var memberDelivered =
                api("POST", "/admin-api/system/notify-template/send-notify", send, token, 1);
        expectCode(memberDelivered, 0);
        String memberMessage = memberDelivered.get("data").toString();
        send.put("userId", "9007199254740993");
        var longDelivered =
                api("POST", "/admin-api/system/notify-template/send-notify", send, token, 1);
        expectCode(longDelivered, 0);
        String longMessage = longDelivered.get("data").toString();
        var adminRecord =
                data(
                        api(
                                "GET",
                                "/admin-api/system/notify-message/get?id=" + adminMessage,
                                null,
                                token,
                                1));
        assertEquals("23013", adminRecord.get("userId").toString());
        assertEquals(2, ((Number) adminRecord.get("userType")).intValue());
        assertEquals("Hello Workspace", adminRecord.get("templateContent"));
        assertEquals(Map.of("name", "Workspace"), adminRecord.get("templateParams"));
        var longRecord =
                data(
                        api(
                                "GET",
                                "/admin-api/system/notify-message/get?id=" + longMessage,
                                null,
                                token,
                                1));
        assertEquals("9007199254740993", longRecord.get("userId").toString());
        assertEquals(1, ((Number) longRecord.get("userType")).intValue());
        var filtered =
                data(
                        api(
                                "GET",
                                "/admin-api/system/notify-message/page?pageNo=1&pageSize=10&userId=9007199254740993&userType=1&templateCode=jss_owned_notify_fixture",
                                null,
                                token,
                                1));
        assertEquals(1, ((Number) filtered.get("total")).intValue());
        template.put("status", 1);
        expectCode(api("PUT", "/admin-api/system/notify-template/update", template, token, 1), 0);
        var ignored = api("POST", "/admin-api/system/notify-template/send-notify", send, token, 1);
        expectCode(ignored, 0);
        assertNull(ignored.get("data"));
        assertEquals(
                1,
                ((Number)
                                data(api(
                                                "GET",
                                                "/admin-api/system/notify-message/page?pageNo=1&pageSize=10&userId=9007199254740993&userType=1&templateCode=jss_owned_notify_fixture",
                                                null,
                                                token,
                                                1))
                                        .get("total"))
                        .intValue());

        // Keep the workspace live while removing all administrative notify permissions.
        assignMenus(reviewer, 23013, List.of(menuId("jss:workspace:access")));
        expectCode(
                api(
                        "GET",
                        "/admin-api/system/notify-message/get?id=" + adminMessage,
                        null,
                        token,
                        1),
                403);
        expectCode(
                api(
                        "GET",
                        "/admin-api/system/notify-message/page?pageNo=1&pageSize=10",
                        null,
                        token,
                        1),
                403);
        expectCode(
                api("POST", "/admin-api/system/notify-template/send-notify", send, token, 1), 403);
        var own =
                data(
                        api(
                                "GET",
                                "/admin-api/system/notify-message/my-get?id=" + adminMessage,
                                null,
                                token,
                                1));
        assertEquals(adminMessage, own.get("id").toString());
        assertEquals(false, own.get("readStatus"));
        for (String messageId : List.of(memberMessage, longMessage, "9223372036854775806")) {
            var hidden =
                    api(
                            "GET",
                            "/admin-api/system/notify-message/my-get?id="
                                    + messageId
                                    + "&userId=23013&userType=1",
                            null,
                            token,
                            1);
            expectCode(hidden, 0);
            assertNull(hidden.get("data"));
        }
        String stranger = login("admin", "jssitstaff");
        var hidden =
                api(
                        "GET",
                        "/admin-api/system/notify-message/my-get?id=" + adminMessage,
                        null,
                        stranger,
                        1);
        expectCode(hidden, 0);
        assertNull(hidden.get("data"));
        expectCode(
                api(
                        "GET",
                        "/admin-api/system/notify-message/my-get?id=" + adminMessage,
                        null,
                        member,
                        1),
                403);
        expectCode(
                api(
                        "GET",
                        "/admin-api/system/notify-message/my-get?id=" + adminMessage,
                        null,
                        null,
                        1),
                401);
        var personal =
                data(
                        api(
                                "GET",
                                "/admin-api/system/notify-message/my-page?pageNo=1&pageSize=10",
                                null,
                                token,
                                1));
        assertEquals(1, ((Number) personal.get("total")).intValue());
        var personalList = (List<?>) personal.get("list");
        assertEquals(adminMessage, ((Map<?, ?>) personalList.get(0)).get("id").toString());
        assertEquals(
                1,
                ((Number)
                                api(
                                                "GET",
                                                "/admin-api/system/notify-message/get-unread-count",
                                                null,
                                                token,
                                                1)
                                        .get("data"))
                        .intValue());
        expectCode(
                api(
                        "PUT",
                        "/admin-api/system/notify-message/update-read?ids="
                                + adminMessage
                                + ","
                                + memberMessage
                                + ","
                                + longMessage,
                        null,
                        token,
                        1),
                0);
        assertEquals(
                true,
                data(api(
                                "GET",
                                "/admin-api/system/notify-message/my-get?id=" + adminMessage,
                                null,
                                token,
                                1))
                        .get("readStatus"));
        assertEquals(
                0,
                ((Number)
                                api(
                                                "GET",
                                                "/admin-api/system/notify-message/get-unread-count",
                                                null,
                                                token,
                                                1)
                                        .get("data"))
                        .intValue());
        expectCode(
                api("PUT", "/admin-api/system/notify-message/update-all-read", null, token, 1), 0);
        for (String messageId : List.of(memberMessage, longMessage)) {
            assertEquals(
                    false,
                    data(api(
                                    "GET",
                                    "/admin-api/system/notify-message/get?id=" + messageId,
                                    null,
                                    reviewer,
                                    1))
                            .get("readStatus"));
        }
        assignMenus(reviewer, 23013, granted);
        expectCode(
                api(
                        "DELETE",
                        "/admin-api/system/notify-template/delete?id=" + templateId,
                        null,
                        token,
                        1),
                0);
        assertNull(
                api("GET", "/admin-api/system/notify-template/get?id=" + templateId, null, token, 1)
                        .get("data"));
        assignMenus(reviewer, 23013, queries);
    }

    private Map<String, Object> awaitMailStatus(String token, String logId, int expected)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        Map<String, Object> log = null;
        do {
            log = data(api("GET", "/admin-api/system/mail-log/get?id=" + logId, null, token, 1));
            if (((Number) log.get("sendStatus")).intValue() == expected) return log;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertEquals(
                expected,
                ((Number) log.get("sendStatus")).intValue(),
                "Actual asynchronous mail status");
        return log;
    }

    private String mailText(jakarta.mail.Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof jakarta.mail.Multipart multi) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multi.getCount(); i++) text.append(mailText(multi.getBodyPart(i)));
            return text.toString();
        }
        return content instanceof String text ? text : "";
    }

    @Test
    void realMailManagementUsesIndependentPermissionsAndDeliversOnlyToOwnedLoopbackSmtp()
            throws Exception {
        String reviewer = login("admin", "jssitreviewer"), member = login("member", "13800009012");
        expectCode(
                api(
                        "POST",
                        "/admin-api/jss/identity/bind",
                        Map.of(
                                "memberUserId",
                                23012,
                                "adminUserId",
                                23012,
                                "reason",
                                "Reviewed owned mail fixture"),
                        reviewer,
                        1),
                0);
        List<Long> queries =
                List.of(
                        menuId("jss:workspace:access"),
                        menuId("system:mail-account:query"),
                        menuId("system:mail-template:query"),
                        menuId("system:mail-log:query"));
        assignMenus(reviewer, 23012, queries);
        String token = issueWorkspace(member);
        try (var smtp = new JssLocalSmtpSink()) {
            var account =
                    new HashMap<String, Object>(
                            Map.of(
                                    "mail",
                                    "sender@example.invalid",
                                    "username",
                                    "isolated-smtp",
                                    "password",
                                    "isolated-smtp-fixture",
                                    "host",
                                    "127.0.0.1",
                                    "port",
                                    smtp.port(),
                                    "sslEnable",
                                    false,
                                    "starttlsEnable",
                                    false));
            var template =
                    new HashMap<String, Object>(
                            Map.of(
                                    "name",
                                    "Owned mail template",
                                    "code",
                                    "jss_owned_mail_fixture",
                                    "accountId",
                                    1,
                                    "nickname",
                                    "Owned SMTP",
                                    "title",
                                    "Isolated Hello {name}",
                                    "content",
                                    "<b>Hello {name}</b>",
                                    "status",
                                    0));
            var send =
                    Map.of(
                            "templateCode",
                            "jss_owned_mail_fixture",
                            "templateParams",
                            Map.of("name", "Workspace"),
                            "toMails",
                            List.of("to@example.invalid"),
                            "ccMails",
                            List.of("cc@example.invalid"),
                            "bccMails",
                            List.of("bcc@example.invalid"));
            for (String kind : List.of("account", "template")) {
                Map<String, Object> body = kind.equals("account") ? account : template;
                expectCode(
                        api("POST", "/admin-api/system/mail-" + kind + "/create", body, token, 1),
                        403);
                var update = new HashMap<>(body);
                update.put("id", 1);
                expectCode(
                        api("PUT", "/admin-api/system/mail-" + kind + "/update", update, token, 1),
                        403);
                expectCode(
                        api(
                                "DELETE",
                                "/admin-api/system/mail-" + kind + "/delete?id=1",
                                null,
                                token,
                                1),
                        403);
            }
            expectCode(
                    api("POST", "/admin-api/system/mail-template/send-mail", send, token, 1), 403);
            assertEquals(0, smtp.transactions);
            var granted = new ArrayList<>(queries);
            for (String kind : List.of("account", "template"))
                for (String action : List.of("create", "update", "delete"))
                    granted.add(menuId("system:mail-" + kind + ":" + action));
            assignMenus(reviewer, 23012, granted);
            String accountId =
                    expectCode(
                                    api(
                                            "POST",
                                            "/admin-api/system/mail-account/create",
                                            account,
                                            token,
                                            1),
                                    0)
                            .get("data")
                            .toString();
            account.put("id", accountId);
            account.put("username", "updated-isolated-smtp");
            expectCode(api("PUT", "/admin-api/system/mail-account/update", account, token, 1), 0);
            var savedAccount =
                    data(
                            api(
                                    "GET",
                                    "/admin-api/system/mail-account/get?id=" + accountId,
                                    null,
                                    token,
                                    1));
            assertEquals("updated-isolated-smtp", savedAccount.get("username"));
            assertEquals(Boolean.FALSE, savedAccount.get("sslEnable"));
            assertEquals(Boolean.FALSE, savedAccount.get("starttlsEnable"));
            var simple =
                    (List<?>)
                            expectCode(
                                            api(
                                                    "GET",
                                                    "/admin-api/system/mail-account/simple-list",
                                                    null,
                                                    token,
                                                    1),
                                            0)
                                    .get("data");
            var selectedAccount =
                    simple.stream()
                            .map(row -> (Map<?, ?>) row)
                            .filter(row -> accountId.equals(row.get("id").toString()))
                            .findFirst()
                            .orElseThrow();
            assertEquals("sender@example.invalid", selectedAccount.get("mail"));
            assertFalse(selectedAccount.containsKey("password"));
            assertInstanceOf(String.class, selectedAccount.get("id"));
            assertEquals("9007199254740993", accountId);
            template.put("accountId", accountId);
            String templateId =
                    expectCode(
                                    api(
                                            "POST",
                                            "/admin-api/system/mail-template/create",
                                            template,
                                            token,
                                            1),
                                    0)
                            .get("data")
                            .toString();
            assertEquals("9007199254740995", templateId);
            template.put("id", templateId);
            template.put("title", "Isolated Updated {name}");
            expectCode(api("PUT", "/admin-api/system/mail-template/update", template, token, 1), 0);
            assertTrue(
                    ((List<?>)
                                    data(api(
                                                    "GET",
                                                    "/admin-api/system/mail-template/get?id="
                                                            + templateId,
                                                    null,
                                                    token,
                                                    1))
                                            .get("params"))
                            .contains("name"));
            String foreign = login("admin", "jssitforeign", 91001);
            expectCode(
                    api(
                            "GET",
                            "/admin-api/system/mail-account/get?id=" + accountId,
                            null,
                            foreign,
                            91001),
                    403);
            expectCode(
                    api("POST", "/admin-api/system/mail-template/send-mail", send, token, 1), 403);
            granted.add(menuId("system:mail-template:send-mail"));
            assignMenus(reviewer, 23012, granted);
            var missing = new HashMap<String, Object>(send);
            missing.put("templateParams", Map.of());
            assertNotEquals(
                    0,
                    ((Number)
                                    api(
                                                    "POST",
                                                    "/admin-api/system/mail-template/send-mail",
                                                    missing,
                                                    token,
                                                    1)
                                            .get("code"))
                            .intValue());
            assertEquals(0, smtp.transactions);
            String logId =
                    expectCode(
                                    api(
                                            "POST",
                                            "/admin-api/system/mail-template/send-mail",
                                            send,
                                            token,
                                            1),
                                    0)
                            .get("data")
                            .toString();
            var captured = smtp.received.poll(12, TimeUnit.SECONDS);
            assertNotNull(captured, "Actual loopback SMTP receipt");
            assertEquals(
                    java.util.Set.of(
                            "<to@example.invalid>",
                            "<cc@example.invalid>",
                            "<bcc@example.invalid>"),
                    java.util.Set.copyOf(captured.recipients()));
            var message =
                    new jakarta.mail.internet.MimeMessage(
                            jakarta.mail.Session.getInstance(new java.util.Properties()),
                            new java.io.ByteArrayInputStream(
                                    captured.mime()
                                            .getBytes(
                                                    java.nio.charset.StandardCharsets.ISO_8859_1)));
            assertEquals("Isolated Updated Workspace", message.getSubject());
            assertTrue(mailText(message).contains("Hello Workspace"));
            assertNull(message.getHeader("Bcc"));
            var delivered = awaitMailStatus(token, logId, 10);
            assertEquals(List.of("bcc@example.invalid"), delivered.get("bccMails"));
            assertFalse(delivered.get("sendMessageId").toString().isBlank());
            smtp.rejectDelivery = true;
            String failed =
                    expectCode(
                                    api(
                                            "POST",
                                            "/admin-api/system/mail-template/send-mail",
                                            send,
                                            token,
                                            1),
                                    0)
                            .get("data")
                            .toString();
            assertFalse(
                    awaitMailStatus(token, failed, 20).get("sendException").toString().isBlank());
            assertEquals(2, smtp.transactions);
            template.put("status", 1);
            expectCode(api("PUT", "/admin-api/system/mail-template/update", template, token, 1), 0);
            String ignored =
                    expectCode(
                                    api(
                                            "POST",
                                            "/admin-api/system/mail-template/send-mail",
                                            send,
                                            token,
                                            1),
                                    0)
                            .get("data")
                            .toString();
            awaitMailStatus(token, ignored, 30);
            assertEquals(2, smtp.transactions);
            var page =
                    data(
                            api(
                                    "GET",
                                    "/admin-api/system/mail-log/page?pageNo=1&pageSize=10&templateId="
                                            + templateId
                                            + "&userId=23012&sendStatus=10",
                                    null,
                                    token,
                                    1));
            assertEquals(1, ((Number) page.get("total")).intValue());
            assignMenus(reviewer, 23012, queries);
            expectCode(
                    api("POST", "/admin-api/system/mail-template/send-mail", send, token, 1), 403);
            assignMenus(reviewer, 23012, granted);
            expectCode(
                    api(
                            "DELETE",
                            "/admin-api/system/mail-template/delete?id=" + templateId,
                            null,
                            token,
                            1),
                    0);
            expectCode(
                    api(
                            "DELETE",
                            "/admin-api/system/mail-account/delete?id=" + accountId,
                            null,
                            token,
                            1),
                    0);
            assertNull(
                    expectCode(
                                    api(
                                            "GET",
                                            "/admin-api/system/mail-account/get?id=" + accountId,
                                            null,
                                            token,
                                            1),
                                    0)
                            .get("data"));
        } finally {
            assignMenus(reviewer, 23012, queries);
        }
    }
}
