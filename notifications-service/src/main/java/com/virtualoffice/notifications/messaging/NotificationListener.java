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
package com.virtualoffice.notifications.messaging;

import com.virtualoffice.notifications.dto.NotificationResponse;
import com.virtualoffice.notifications.service.EmailDispatchService;
import com.virtualoffice.notifications.service.EmailIdempotencyService;
import com.virtualoffice.notifications.service.InAppNotificationService;
import com.virtualoffice.notifications.service.NotificationPushService;
import com.virtualoffice.notifications.template.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final EmailDispatchService emailDispatchService;
    private final EmailIdempotencyService emailIdempotencyService;
    private final InAppNotificationService inAppNotificationService;
    private final NotificationPushService notificationPushService;

    @RabbitListener(queues = "${notifications.queue}")
    public void handle(NotificationEvent event) {
        switch (event.getType()) {
            case SIGNUP_SUCCESS, LOGIN_SUCCESS, OTP, PASSWORD_RESET_SUCCESS -> handleEmail(event);
            case TASK_ASSIGNED -> handleInApp(event);
        }
    }

    private void handleEmail(NotificationEvent event) {
        // Dedup must happen BEFORE the recipient check so a duplicate event
        // without a recipient (malformed redelivery) doesn't burn the retry
        // budget. But it must happen AFTER successful dispatch, not before -
        // otherwise a transient SMTP failure on attempt 1 claims the key
        // and attempts 2..5 silently no-op without ever retrying the send.
        if (!emailIdempotencyService.tryClaim(event.getEventId())) {
            log.debug("Email event {} already processed, skipping", event.getEventId());
            return;
        }

        Object recipient = event.getPayload() != null ? event.getPayload().get("email") : null;
        if (!(recipient instanceof String to) || to.isBlank()) {
            log.error("Missing 'email' in payload for {} event {}", event.getType(), event.getEventId());
            throw new AmqpRejectAndDontRequeueException("missing recipient email");
        }

        try {
            emailDispatchService.dispatch(
                    EmailTemplate.fromType(event.getType()),
                    to,
                    stringifyPayload(event.getPayload()));
        } catch (RuntimeException e) {
            // Dispatch failed; release the claim so Spring AMQP's retry actually
            // gets a chance to re-attempt the SMTP send. Without this, attempt
            // 1's claim would block attempts 2..5.
            emailIdempotencyService.release(event.getEventId());
            throw e;
        }
    }

    // Mongo insert is durable; the WebSocket push is best-effort. If the user
    // is offline, the push silently drops. Duplicate redeliveries skip both
    // (createFromEvent returns empty on a known eventId).
    private void handleInApp(NotificationEvent event) {
        inAppNotificationService.createFromEvent(event)
                .ifPresent(saved -> notificationPushService.push(
                        saved.getUserId(),
                        NotificationResponse.from(saved)));
    }

    // The renderer expects Map<String, String>; payload values arrive as Object
    // on the wire (ints, instants, ...). Defensive against a null payload even
    // though handleEmail's recipient check guards against that today.
    private Map<String, String> stringifyPayload(Map<String, Object> payload) {
        Map<String, String> out = new HashMap<>();
        if (payload == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? "" : e.getValue().toString());
        }
        return out;
    }
}
