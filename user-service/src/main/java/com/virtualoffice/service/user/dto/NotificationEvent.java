package com.virtualoffice.service.user.dto;

import com.virtualoffice.service.user.domain.enumuration.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationEvent {
    private String eventId;
    private NotificationType type;
    private Instant occurredAt;
    private Map<String, Object> payload;
}