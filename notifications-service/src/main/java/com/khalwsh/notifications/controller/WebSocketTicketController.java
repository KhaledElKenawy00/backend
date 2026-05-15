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

    @PostMapping("/ws-ticket")
    public Map<String, String> createTicket() {
        Long userId = userContext.currentUserId();
        return Map.of("ticket", ticketService.createTicket(userId));
    }
}
