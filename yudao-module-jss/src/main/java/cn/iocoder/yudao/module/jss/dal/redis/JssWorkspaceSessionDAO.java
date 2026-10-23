package cn.iocoder.yudao.module.jss.dal.redis;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.jss.service.JssWorkspaceSession;

import jakarta.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JssWorkspaceSessionDAO {
    @Resource private StringRedisTemplate redis;
    private static final String PREFIX = "jss:workspace:";
    private static final DefaultRedisScript<Long> SAVE =
            new DefaultRedisScript<>(
                    "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3]); "
                            + "redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3]); return 1",
                    Long.class);
    private static final DefaultRedisScript<Long> DELETE =
            new DefaultRedisScript<>(
                    "redis.call('DEL', KEYS[1]); if redis.call('GET', KEYS[2]) == ARGV[1] then "
                            + "redis.call('DEL', KEYS[2]); end; return 1",
                    Long.class);

    public void save(String token, JssWorkspaceSession session) {
        session.setTokenDigest(DigestUtil.sha256Hex(token));
        long ttl = session.getExpiresAt() - System.currentTimeMillis();
        if (ttl <= 0) throw new IllegalArgumentException("Session already expired");
        redis.execute(
                SAVE,
                List.of(key(session), memberKey(session)),
                JsonUtils.toJsonString(session),
                session.getTokenDigest(),
                String.valueOf(ttl));
    }

    public JssWorkspaceSession get(String token) {
        return JsonUtils.parseObject(
                redis.opsForValue().get(PREFIX + DigestUtil.sha256Hex(token)),
                JssWorkspaceSession.class);
    }

    public boolean isCurrent(JssWorkspaceSession session) {
        return session.getTokenDigest().equals(redis.opsForValue().get(memberKey(session)));
    }

    public void delete(JssWorkspaceSession session) {
        redis.execute(DELETE, List.of(key(session), memberKey(session)), session.getTokenDigest());
    }

    private String key(JssWorkspaceSession session) {
        return PREFIX + session.getTokenDigest();
    }

    private String memberKey(JssWorkspaceSession session) {
        return PREFIX
                + "member:"
                + session.getTenantId()
                + ":"
                + DigestUtil.sha256Hex(session.getMemberAccessToken());
    }
}
