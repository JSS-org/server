package cn.iocoder.yudao.module.jss.framework;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.websocket.core.security.WebSocketSessionAuthorization;
import cn.iocoder.yudao.framework.websocket.core.util.WebSocketFrameworkUtils;
import cn.iocoder.yudao.module.jss.service.JssWorkspaceService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Keeps a workspace socket subject to the same live identity and RBAC checks as HTTP. */
@Component
@RequiredArgsConstructor
@Slf4j
public class JssWebSocketAuthorization implements WebSocketSessionAuthorization {
    static final String CREDENTIAL = JssWebSocketAuthorization.class.getName() + ".credential";
    private static final Set<String> KEFU_READ =
            Set.of("promotion:kefu-conversation:query", "promotion:kefu-message:query");
    private final OAuth2TokenCommonApi tokens;
    private final PermissionApi permissions;
    private final SecurityProperties security;

    @Override
    public boolean prepare(ServerHttpRequest request, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servlet) {
            String token =
                    SecurityFrameworkUtils.obtainAuthorization(
                            servlet.getServletRequest(),
                            security.getTokenHeader(),
                            security.getTokenParameter());
            if (token != null && !token.isBlank()) attributes.put(CREDENTIAL, token);
        }
        LoginUser user = (LoginUser) attributes.get(WebSocketFrameworkUtils.ATTRIBUTE_LOGIN_USER);
        return user != null
                && (user.getPermissionLimits() == null || attributes.containsKey(CREDENTIAL));
    }

    @Override
    public boolean isValid(WebSocketSession session) {
        LoginUser user = WebSocketFrameworkUtils.getLoginUser(session);
        if (user == null) return false;
        Object value = session.getAttributes().get(CREDENTIAL);
        // Native sockets keep their existing lifecycle. Kefu deliveries below still check live
        // auth.
        if (user.getPermissionLimits() == null
                && !(value instanceof String token
                        && token.startsWith(JssWorkspaceService.TOKEN_PREFIX))) return true;
        return checked(session) != null;
    }

    @Override
    public boolean canSend(WebSocketSession session, String messageType) {
        if (!Set.of("kefu_message_type", "kefu_message_read_status_change").contains(messageType))
            return true;
        LoginUser user = WebSocketFrameworkUtils.getLoginUser(session);
        // A missing publisher tenant must never turn a kefu delivery into a global broadcast.
        if (user == null
                || user.getTenantId() == null
                || !Objects.equals(TenantContextHolder.getTenantId(), user.getTenantId()))
            return false;
        OAuth2AccessTokenCheckRespDTO current = checked(session);
        if (current == null) return false;
        if (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType())) return true;
        return TenantUtils.execute(
                user.getTenantId(),
                () -> {
                    Set<String> ceiling = current.getPermissionLimits();
                    return (ceiling == null || ceiling.containsAll(KEFU_READ))
                            && permissions
                                    .getEnabledMenuPermissions(user.getId())
                                    .containsAll(KEFU_READ)
                            && KEFU_READ.stream()
                                    .allMatch(
                                            permission ->
                                                    permissions.hasAnyPermissions(
                                                            user.getId(), permission));
                });
    }

    private OAuth2AccessTokenCheckRespDTO checked(WebSocketSession session) {
        LoginUser user = WebSocketFrameworkUtils.getLoginUser(session);
        Object value = session.getAttributes().get(CREDENTIAL);
        if (user == null
                || !(value instanceof String token)
                || token.isBlank()
                || user.getTenantId() == null) return null;
        try {
            return TenantUtils.execute(
                user.getTenantId(),
                () -> {
                    OAuth2AccessTokenCheckRespDTO current = tokens.checkAccessToken(token);
                    return current != null
                                    && Objects.equals(current.getUserId(), user.getId())
                                    && Objects.equals(current.getUserType(), user.getUserType())
                                    && Objects.equals(current.getTenantId(), user.getTenantId())
                            ? current
                            : null;
                });
        } catch (RuntimeException denied) {
            // Never log the bearer/parent credential or exception messages that may contain it.
            Throwable reason = denied;
            for (int depth = 0; depth < 16 && reason.getCause() != null
                    && reason.getCause() != reason; depth++) {
                reason = reason.getCause();
            }
            log.debug("[checked][socket={} user={} tenant={} rejection={} code={} trace={}]",
                    session.getId(), user.getId(), user.getTenantId(), reason.getClass().getSimpleName(),
                    reason instanceof cn.iocoder.yudao.framework.common.exception.ServiceException service ? service.getCode() : null,
                    java.util.Arrays.stream(reason.getStackTrace()).limit(8).toList());
            throw denied;
        }
    }
}
