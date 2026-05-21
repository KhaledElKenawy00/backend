package com.virtualoffice.notifications.messaging;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class NotificationEvent {
    private String eventId;
    private NotificationType type;
    private Instant occurredAt;
    private Map<String, Object> payload;
}
