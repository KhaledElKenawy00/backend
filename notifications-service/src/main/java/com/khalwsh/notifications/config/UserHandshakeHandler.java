package com.khalwsh.notifications.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Binds the userId that WebSocketHandshakeInterceptor stashed into a session
 * Principal whose getName() is the userId as String.
 *
 * That's what convertAndSendToUser(userId, "/queue/notifications", ...) routes
 * against. Without this handler, Spring would use a random session id as the
 * principal name and the push could not reach the right user.
 */
@Component
public class UserHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {

        Object userId = attributes.get(WebSocketHandshakeInterceptor.USER_ID_ATTR);
        if (userId == null) {
            return null;   // shouldn't happen — interceptor rejects before this runs
        }
        String name = userId.toString();
        return () -> name;   // Principal is a functional interface with getName()
    }
}
