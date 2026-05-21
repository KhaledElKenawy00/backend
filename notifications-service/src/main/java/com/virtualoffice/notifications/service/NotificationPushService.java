/*
 * Copyright (c) 2025 My Virtual Office
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package com.virtualoffice.notifications.service;

import com.virtualoffice.notifications.dto.NotificationResponse;
import com.virtualoffice.notifications.dto.WebSocketEvent;
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
