package com.khalwsh.notifications.controller;

import com.khalwsh.notifications.service.WebSocketTicketService;
import com.khalwsh.notifications.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class WebSocketTicketController {

    private final WebSocketTicketService ticketService;
    private final UserContext userContext;

    /**
     * Mints a one-time WS handshake ticket bound to the caller's X-User-Id.
     * The client passes it back as ?ticket= on the WS connect.
     */
    @PostMapping("/ws-ticket")
    public Map<String, String> createTicket() {
        Long userId = userContext.currentUserId();
        return Map.of("ticket", ticketService.createTicket(userId));
    }
}
