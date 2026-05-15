package com.khalwsh.notifications.service;

import com.khalwsh.notifications.dto.NotificationResponse;
import com.khalwsh.notifications.dto.WebSocketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPushService {

    private static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    // Push is best-effort: if the user has no open WS session, Spring drops
    // the frame silently. Any failure here must not fail the listener,
    // because the notification is already durably saved in Mongo.
    public void push(Long userId, NotificationResponse payload) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    DESTINATION,
                    new WebSocketEvent("NEW_NOTIFICATION", payload));
        } catch (Exception e) {
            log.warn("Failed to push notification to user {}: {}", userId, e.getMessage());
        }
    }
}
