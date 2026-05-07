package com.khalwsh.chat_service.service;

import java.util.Map;

public interface WebSocketTicketService {

    String createTicket(Integer userId, String role);

    // returns {userId, userRole} on success, or null if the ticket is invalid/expired/already-used
    Map<String, Object> validateAndConsumeTicket(String ticket);
}
