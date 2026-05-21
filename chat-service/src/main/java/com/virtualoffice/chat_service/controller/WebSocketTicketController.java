package com.virtualoffice.chat_service.controller;

import com.virtualoffice.chat_service.dto.response.WebSocketTicketResponse;
import com.virtualoffice.chat_service.service.WebSocketTicketService;
import com.virtualoffice.chat_service.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class WebSocketTicketController {

    private final WebSocketTicketService webSocketTicketService;

    @PostMapping("/ws-ticket")
    public ResponseEntity<WebSocketTicketResponse> createTicket(HttpServletRequest httpRequest) {
        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        String ticket = webSocketTicketService.createTicket(user.getUserId(), user.getRole());
        return ResponseEntity.ok(WebSocketTicketResponse.builder().ticket(ticket).build());
    }
}
