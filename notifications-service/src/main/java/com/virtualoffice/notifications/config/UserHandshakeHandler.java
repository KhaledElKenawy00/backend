package com.virtualoffice.notifications.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

// Binds the userId stashed by WebSocketHandshakeInterceptor as the session
// Principal name, so SimpMessagingTemplate.convertAndSendToUser(userId, ...)
// routes to the right session. Without this, Spring uses a random session id
// and the user destination cannot resolve.
@Component
public class UserHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {

        Object userId = attributes.get(WebSocketHandshakeInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return null;
        }
        String name = userId.toString();
        return () -> name;
    }
}
