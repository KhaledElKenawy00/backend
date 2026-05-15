package com.khalwsh.notifications.service;

import com.khalwsh.notifications.dto.NotificationResponse;
import com.khalwsh.notifications.dto.WebSocketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around SimpMessagingTemplate for sending live notifications
 * to a single user's STOMP session(s). Spring routes the message based on
 * the principal name set by UserHandshakeHandler — only that user's
 * subscribed clients receive it.
 *
 * Best-effort: if the user is offline, the message is silently dropped at
 * the broker level. The notification is already durably saved in Mongo by
 * the time push is attempted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPushService {

    private static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void push(Long userId, NotificationResponse payload) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    DESTINATION,
                    new WebSocketEvent("NEW_NOTIFICATION", payload));
        } catch (Exception e) {
            // Push failure must not fail the listener — the doc is already saved
            log.warn("Failed to push notification to user {}: {}", userId, e.getMessage());
        }
    }
}
