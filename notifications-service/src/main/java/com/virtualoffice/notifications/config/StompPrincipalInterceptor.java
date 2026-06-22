package com.virtualoffice.notifications.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

// Sets the STOMP session Principal from the userId stashed in the WebSocket
// session attributes by WebSocketHandshakeInterceptor, so that
// SimpMessagingTemplate.convertAndSendToUser(userId, ...) can route correctly.
@Component
@Slf4j
public class StompPrincipalInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null) {
                Object raw = attrs.get(WebSocketHandshakeInterceptor.USER_ID_ATTR);
                if (raw != null) {
                    String name = raw.toString();
                    accessor.setUser(() -> name);
                    log.debug("STOMP CONNECT — principal set to userId={}", name);
                } else {
                    log.warn("STOMP CONNECT — no {} in session attributes", WebSocketHandshakeInterceptor.USER_ID_ATTR);
                }
            }
        }
        return message;
    }
}
