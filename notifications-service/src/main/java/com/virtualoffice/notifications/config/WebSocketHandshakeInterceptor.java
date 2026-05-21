package com.virtualoffice.notifications.config;

import com.virtualoffice.notifications.service.WebSocketTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTR = "wsUserId";

    private final WebSocketTicketService ticketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("WS handshake rejected: not a servlet request");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String ticket = servletRequest.getServletRequest().getParameter("ticket");
        Optional<Long> userId = ticketService.consumeTicket(ticket);

        if (userId.isEmpty()) {
            log.warn("WS handshake rejected: invalid or missing ticket");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(USER_ID_ATTR, userId.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
