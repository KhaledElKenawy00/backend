package com.khalwsh.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Standardized STOMP push envelope: { action, payload }.
 * Matches chat-service's shape so the frontend can reuse its parser.
 */
@Data
@AllArgsConstructor
public class WebSocketEvent {
    private String action;
    private Object payload;
}
