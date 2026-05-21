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

import com.virtualoffice.notifications.model.Notification;
import com.virtualoffice.notifications.service.EmailDispatchService;
import com.virtualoffice.notifications.service.EmailIdempotencyService;
import com.virtualoffice.notifications.service.InAppNotificationService;
import com.virtualoffice.notifications.service.NotificationPushService;
import com.virtualoffice.notifications.template.EmailTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock private EmailDispatchService emailDispatchService;
    @Mock private EmailIdempotencyService emailIdempotencyService;
    @Mock private InAppNotificationService inAppNotificationService;
    @Mock private NotificationPushService notificationPushService;

    @InjectMocks private NotificationListener listener;

    private NotificationEvent emailEvent(NotificationType type, Map<String, Object> payload) {
        NotificationEvent e = new NotificationEvent();
        e.setEventId("evt-" + type);
        e.setType(type);
        e.setOccurredAt(Instant.parse("2026-05-16T14:00:00Z"));
        e.setPayload(payload);
        return e;
    }

    @Test
    void emailEventDispatchesToCorrectTemplate() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "user@x.com");
        payload.put("firstName", "Khaled");

        listener.handle(emailEvent(NotificationType.OTP, payload));

        verify(emailDispatchService).dispatch(eq(EmailTemplate.OTP), eq("user@x.com"), any());
        verifyNoInteractions(inAppNotificationService, notificationPushService);
    }

    @Test
    void allFourEmailTypesMapToCorrectTemplate() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);
        Map<String, Object> payload = Map.of("email", "u@x.com");

        listener.handle(emailEvent(NotificationType.SIGNUP_SUCCESS, payload));
        verify(emailDispatchService).dispatch(eq(EmailTemplate.SIGNUP_SUCCESS), any(), any());

        listener.handle(emailEvent(NotificationType.LOGIN_SUCCESS, payload));
        verify(emailDispatchService).dispatch(eq(EmailTemplate.LOGIN_SUCCESS), any(), any());

        listener.handle(emailEvent(NotificationType.OTP, payload));
        verify(emailDispatchService).dispatch(eq(EmailTemplate.OTP), any(), any());

        listener.handle(emailEvent(NotificationType.PASSWORD_RESET_SUCCESS, payload));
        verify(emailDispatchService).dispatch(eq(EmailTemplate.PASSWORD_RESET_SUCCESS), any(), any());
    }

    @Test
    void duplicateEmailEventIsSkipped() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(false);

        listener.handle(emailEvent(NotificationType.OTP,
                Map.of("email", "user@x.com", "firstName", "Khaled")));

        verifyNoInteractions(emailDispatchService);
    }

    @Test
    void emailWithMissingRecipientIsRejectedWithoutRequeue() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);

        assertThatThrownBy(() -> listener.handle(emailEvent(NotificationType.OTP, Map.of("firstName", "Khaled"))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(emailDispatchService);
    }

    @Test
    void dispatchFailureReleasesIdempotencyClaimSoRetriesActuallyRetry() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);
        doThrow(new RuntimeException("SMTP timeout"))
                .when(emailDispatchService).dispatch(any(), any(), any());

        assertThatThrownBy(() -> listener.handle(emailEvent(NotificationType.OTP,
                Map.of("email", "u@x.com"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP timeout");

        // Spring AMQP will retry the listener; we must have released the
        // claim so attempt 2 doesn't silently skip.
        verify(emailIdempotencyService).release("evt-OTP");
    }

    @Test
    void dispatchSuccessKeepsIdempotencyClaim() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);

        listener.handle(emailEvent(NotificationType.OTP, Map.of("email", "u@x.com")));

        verify(emailDispatchService).dispatch(any(), any(), any());
        verify(emailIdempotencyService, never()).release(any());
    }

    @Test
    void emailWithBlankRecipientIsRejected() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);

        assertThatThrownBy(() -> listener.handle(emailEvent(NotificationType.OTP, Map.of("email", "   "))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void emailWithNonStringRecipientIsRejected() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", 42);  // wrong type

        assertThatThrownBy(() -> listener.handle(emailEvent(NotificationType.OTP, payload)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void emailWithNullPayloadIsRejected() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);

        assertThatThrownBy(() -> listener.handle(emailEvent(NotificationType.OTP, null)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void taskAssignedFlowsToInAppAndPushesOnSuccess() {
        Notification saved = Notification.builder().id("abc").userId(12L).build();
        when(inAppNotificationService.createFromEvent(any())).thenReturn(Optional.of(saved));

        NotificationEvent event = new NotificationEvent();
        event.setEventId("t-1");
        event.setType(NotificationType.TASK_ASSIGNED);
        event.setOccurredAt(Instant.now());
        event.setPayload(Map.of("assigneeUserId", 12));

        listener.handle(event);

        verify(notificationPushService).push(eq(12L), any());
        verifyNoInteractions(emailDispatchService, emailIdempotencyService);
    }

    @Test
    void taskAssignedDuplicateSkipsPush() {
        when(inAppNotificationService.createFromEvent(any())).thenReturn(Optional.empty());

        NotificationEvent event = new NotificationEvent();
        event.setEventId("t-1");
        event.setType(NotificationType.TASK_ASSIGNED);
        event.setOccurredAt(Instant.now());
        event.setPayload(Map.of("assigneeUserId", 12));

        listener.handle(event);

        verifyNoInteractions(notificationPushService);
    }

    @Test
    void payloadValuesAreStringifiedBeforeRenderer() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "k@x.com");
        payload.put("firstName", "Khaled");
        payload.put("loginAt", "2026-05-16T14:00:00Z");
        payload.put("port", 587);   // non-string

        listener.handle(emailEvent(NotificationType.LOGIN_SUCCESS, payload));

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emailDispatchService).dispatch(eq(EmailTemplate.LOGIN_SUCCESS), eq("k@x.com"), captor.capture());

        Map<String, String> stringified = captor.getValue();
        assertThat(stringified).containsEntry("port", "587");
        assertThat(stringified).containsEntry("firstName", "Khaled");
    }

    @Test
    void payloadNullValuesBecomeEmptyStrings() {
        when(emailIdempotencyService.tryClaim(any())).thenReturn(true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "k@x.com");
        payload.put("firstName", null);

        listener.handle(emailEvent(NotificationType.SIGNUP_SUCCESS, payload));

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emailDispatchService).dispatch(any(), any(), captor.capture());

        assertThat(captor.getValue().get("firstName")).isEmpty();
    }
}
