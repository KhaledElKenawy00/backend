package com.virtualoffice.notifications.dto;

import com.virtualoffice.notifications.messaging.NotificationType;
import com.virtualoffice.notifications.model.Notification;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class NotificationResponse {

    private String id;
    private NotificationType type;
    private String title;
    private String body;
    private Map<String, Object> data;
    private boolean read;
    private Instant createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .data(n.getData())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
