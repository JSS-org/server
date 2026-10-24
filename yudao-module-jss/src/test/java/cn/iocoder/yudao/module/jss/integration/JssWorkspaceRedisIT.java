package cn.iocoder.yudao.module.jss.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.jss.dal.redis.JssWorkspaceSessionDAO;
import cn.iocoder.yudao.module.jss.service.JssWorkspaceSession;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Actual DAO and Redis, with unique test sessions; no Spring context, auth doubles or FLUSHDB. */
@EnabledIfEnvironmentVariable(named = "JSS_TEST_REDIS_RUN_ID", matches = "[a-f0-9]{40}")
class JssWorkspaceRedisIT {
    private static RedissonConnectionFactory connections;
    private static RedissonClient client;
    private static StringRedisTemplate redis;
    private static JssWorkspaceSessionDAO dao;
    private final List<JssWorkspaceSession> created = new ArrayList<>();

    @BeforeAll
    static void connectToOwnedRedis() throws Exception {
        int port = Integer.parseInt(System.getenv("JSS_TEST_REDIS_PORT"));
        assertEquals(16379, port);
        var config = new Config();
        config.setThreads(2).setNettyThreads(2);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + port)
                .setPassword(System.getenv("JSS_TEST_REDIS_PASSWORD"))
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(2)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        client = Redisson.create(config);
        connections = new RedissonConnectionFactory(client);
        connections.afterPropertiesSet();
        try (var connection = connections.getConnection()) {
            assertEquals(
                    System.getenv("JSS_TEST_REDIS_RUN_ID"),
                    connection.serverCommands().info("server").getProperty("run_id"));
        }
        redis = new StringRedisTemplate(connections);
        dao = new JssWorkspaceSessionDAO();
        ReflectionTestUtils.setField(dao, "redis", redis);
    }

    @AfterEach
    void removeOnlyTestSessions() {
        created.forEach(dao::delete);
    }

    @AfterAll
    static void closeClient() {
        if (client != null) client.shutdown();
    }

    private JssWorkspaceSession session(long tenant, String parent, Duration lifetime) {
        var session =
                new JssWorkspaceSession()
                        .setTenantId(tenant)
                        .setMemberUserId(101L)
                        .setAdminUserId(201L)
                        .setIdentityId(301L)
                        .setBindingVersion(1L)
                        .setMemberAccessToken(parent)
                        .setExpiresAt(System.currentTimeMillis() + lifetime.toMillis());
        created.add(session);
        return session;
    }

    private String token() {
        return "jssw_isolated_" + UUID.randomUUID();
    }

    private String parentKey(JssWorkspaceSession session) {
        return "jss:workspace:member:"
                + session.getTenantId()
                + ":"
                + DigestUtil.sha256Hex(session.getMemberAccessToken());
    }

    @Test
    void actualSessionAndParentPointerUseDigestsAndBoundedTtl() {
        String token = token();
        var session = session(1, "isolated-parent-" + UUID.randomUUID(), Duration.ofSeconds(20));
        dao.save(token, session);
        var loaded = dao.get(token);
        assertEquals(session.getTenantId(), loaded.getTenantId());
        assertEquals(session.getMemberUserId(), loaded.getMemberUserId());
        assertEquals(session.getAdminUserId(), loaded.getAdminUserId());
        assertEquals(session.getBindingVersion(), loaded.getBindingVersion());
        assertEquals(session.getMemberAccessToken(), loaded.getMemberAccessToken());
        assertEquals(session.getExpiresAt(), loaded.getExpiresAt());
        assertEquals(DigestUtil.sha256Hex(token), loaded.getTokenDigest());
        assertTrue(dao.isCurrent(loaded));
        assertFalse(Boolean.TRUE.equals(redis.hasKey("jss:workspace:" + token)));
        long sessionTtl =
                redis.getExpire("jss:workspace:" + loaded.getTokenDigest(), TimeUnit.MILLISECONDS);
        long parentTtl = redis.getExpire(parentKey(loaded), TimeUnit.MILLISECONDS);
        assertTrue(sessionTtl > 0 && sessionTtl <= 20000);
        assertTrue(parentTtl > 0 && parentTtl <= sessionTtl);
        assertTrue(sessionTtl - parentTtl < 1000);
    }

    @Test
    void staleDeletionCannotRemoveReplacementAndTenantPointersAreIndependent() {
        String parent = "isolated-parent-" + UUID.randomUUID();
        String oldToken = token(), newToken = token(), otherToken = token();
        var old = session(1, parent, Duration.ofSeconds(20));
        var replacement = session(1, parent, Duration.ofSeconds(20));
        var otherTenant = session(2, parent, Duration.ofSeconds(20));
        dao.save(oldToken, old);
        dao.save(newToken, replacement);
        assertFalse(dao.isCurrent(old));
        assertTrue(dao.isCurrent(replacement));
        dao.delete(old);
        assertNull(dao.get(oldToken));
        assertNotNull(dao.get(newToken));
        assertTrue(dao.isCurrent(replacement));
        dao.save(otherToken, otherTenant);
        assertTrue(dao.isCurrent(otherTenant));
        assertTrue(dao.isCurrent(replacement));
        dao.delete(otherTenant);
        assertTrue(dao.isCurrent(replacement));
    }

    @Test
    void sessionAndPointerExpireTogetherOnRealRedis() throws Exception {
        String token = token();
        var session = session(1, "isolated-parent-" + UUID.randomUUID(), Duration.ofMillis(400));
        dao.save(token, session);
        assertTrue(dao.isCurrent(session));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (dao.get(token) != null && System.nanoTime() < deadline) Thread.sleep(20);
        assertNull(dao.get(token));
        assertFalse(dao.isCurrent(session));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(parentKey(session))));
    }

    @Test
    void expiredInputDoesNotCreateRedisState() {
        String token = token();
        var session = session(1, "isolated-parent-" + UUID.randomUUID(), Duration.ofSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> dao.save(token, session));
        assertNull(dao.get(token));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(parentKey(session))));
    }
}
