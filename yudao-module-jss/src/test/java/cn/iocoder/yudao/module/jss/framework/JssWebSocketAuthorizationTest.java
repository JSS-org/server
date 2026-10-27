package cn.iocoder.yudao.module.jss.framework;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.websocket.core.sender.local.LocalWebSocketMessageSender;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionManagerImpl;
import cn.iocoder.yudao.framework.websocket.core.util.WebSocketFrameworkUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

class JssWebSocketAuthorizationTest {
    private final OAuth2TokenCommonApi tokens = mock(OAuth2TokenCommonApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final JssWebSocketAuthorization policy =
            new JssWebSocketAuthorization(tokens, permissions, new SecurityProperties());
    private final WebSocketSessionManagerImpl manager =
            new WebSocketSessionManagerImpl(List.of(policy));
    private final LocalWebSocketMessageSender sender = new LocalWebSocketMessageSender(manager);
    private final Set<String> read =
            Set.of("promotion:kefu-conversation:query", "promotion:kefu-message:query");

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private WebSocketSession socket(String name, long tenant, String token) {
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(name);
        when(socket.isOpen()).thenReturn(true);
        var attributes = new HashMap<String, Object>();
        WebSocketFrameworkUtils.setLoginUser(
                new LoginUser()
                        .setId(20L)
                        .setUserType(2)
                        .setTenantId(tenant)
                        .setPermissionLimits(read),
                attributes);
        attributes.put(JssWebSocketAuthorization.CREDENTIAL, token);
        when(socket.getAttributes()).thenReturn(attributes);
        manager.addSession(socket);
        return socket;
    }

    private OAuth2AccessTokenCheckRespDTO grant(String token, long tenant) {
        var dto =
                new OAuth2AccessTokenCheckRespDTO()
                        .setUserId(20L)
                        .setUserType(2)
                        .setTenantId(tenant)
                        .setPermissionLimits(read);
        when(tokens.checkAccessToken(token))
                .thenAnswer(
                        call -> {
                            assertEquals(tenant, TenantContextHolder.getTenantId());
                            return dto;
                        });
        when(permissions.getEnabledMenuPermissions(20L)).thenReturn(read);
        read.forEach(
                permission ->
                        when(permissions.hasAnyPermissions(20L, permission)).thenReturn(true));
        return dto;
    }

    @Test
    void deliveryUsesLiveCredentialAndModulePermissions() throws Exception {
        var socket = socket("allowed", 1, "jssw_test");
        var dto = grant("jssw_test", 1);
        TenantContextHolder.setTenantId(1L);
        sender.send(2, "kefu_message_type", "{}");
        verify(socket).sendMessage(any());
        dto.setPermissionLimits(Set.of("jss:workspace:access"));
        sender.send(2, "kefu_message_type", "{}");
        verify(socket, times(1)).sendMessage(any());
    }

    @Test
    void expiredOrRevokedSocketIsRemovedAndClosedBeforeSending() throws Exception {
        var socket = socket("revoked", 1, "jssw_revoked");
        when(tokens.checkAccessToken("jssw_revoked"))
                .thenThrow(new IllegalStateException("revoked"));
        TenantContextHolder.setTenantId(1L);
        sender.send(2, 20L, "kefu_message_type", "{}");
        verify(socket, never()).sendMessage(any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        assertNull(manager.getSession("revoked"));
    }

    @Test
    void mixedTenantSessionsForSameUserAreFilteredIndividuallyForAllTargets() throws Exception {
        var first = socket("tenant1", 1, "jssw_one");
        var second = socket("tenant2", 2, "jssw_two");
        grant("jssw_one", 1);
        grant("jssw_two", 2);
        TenantContextHolder.setTenantId(2L);
        sender.send(2, "kefu_message_type", "{}");
        sender.send(2, 20L, "kefu_message_type", "{}");
        sender.send("tenant1", "kefu_message_type", "{}");
        sender.send("tenant2", "kefu_message_type", "{}");
        verify(first, never()).sendMessage(any());
        verify(second, times(3)).sendMessage(any());
        assertEquals(2L, TenantContextHolder.getTenantId());
    }

    @Test
    void missingPublisherTenantCannotBroadcastKefuGlobally() throws Exception {
        var socket = socket("no-publisher-tenant", 1, "jssw_one");
        grant("jssw_one", 1);
        sender.send(2, "kefu_message_type", "{}");
        verify(socket, never()).sendMessage(any());
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void changedSubjectCannotReuseAnOldSocketAndInboundValidationAlsoClosesIt() throws Exception {
        var socket = socket("old-subject", 1, "jssw_one");
        grant("jssw_one", 1).setUserId(21L);
        assertFalse(manager.isValid(socket));
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void privilegedNativeSocketStillNeedsCurrentKefuPermission() throws Exception {
        var socket = socket("native", 1, "native-token");
        WebSocketFrameworkUtils.getLoginUser(socket).setPermissionLimits(null);
        grant("native-token", 1).setPermissionLimits(null);
        when(permissions.getEnabledMenuPermissions(20L)).thenReturn(Set.of());
        TenantContextHolder.setTenantId(1L);
        sender.send(2, "kefu_message_read_status_change", "{}");
        verify(socket, never()).sendMessage(any());
    }
}
