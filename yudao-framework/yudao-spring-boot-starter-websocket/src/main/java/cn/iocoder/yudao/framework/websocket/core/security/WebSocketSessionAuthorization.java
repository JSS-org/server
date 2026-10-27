package cn.iocoder.yudao.framework.websocket.core.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/** Application policies applied at handshake, inbound use and every outbound delivery. */
public interface WebSocketSessionAuthorization {
    default boolean prepare(ServerHttpRequest request, Map<String, Object> attributes) {
        return true;
    }

    default boolean isValid(WebSocketSession session) {
        return true;
    }

    default boolean canSend(WebSocketSession session, String messageType) {
        return true;
    }
}
