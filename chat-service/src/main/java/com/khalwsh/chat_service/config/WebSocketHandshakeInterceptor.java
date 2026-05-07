package com.khalwsh.chat_service.config;

import com.khalwsh.chat_service.service.WebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

// validates ?ticket={...} on the WS handshake and stashes (userId, userRole)
// into the session so STOMP handlers downstream can read them
@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final WebSocketTicketService webSocketTicketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            String ticket = servletRequest.getServletRequest().getParameter("ticket");
            Map<String, Object> ticketData = webSocketTicketService.validateAndConsumeTicket(ticket);

            if (ticketData == null) {
                return false;
            }

            attributes.put("userId", ticketData.get("userId"));
            attributes.put("userRole", ticketData.get("userRole"));
            return true;
        }

        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
