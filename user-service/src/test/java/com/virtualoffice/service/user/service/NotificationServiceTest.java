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
package com.virtualoffice.service.user.service;

import com.virtualoffice.service.user.domain.entity.User;
import com.virtualoffice.service.user.domain.enumuration.NotificationType;
import com.virtualoffice.service.user.notifications.NotificationPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    private User user() {
        User u = new User();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setFirstName("Test");
        return u;
    }

    @Test
    void registerNotificationPublishesSignupEvent() {
        notificationService.registerNotification(user());

        verify(notificationPublisher).publish(
                org.mockito.ArgumentMatchers.eq(NotificationType.SIGNUP_SUCCESS), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("email", "user@example.com")
                .containsEntry("firstName", "Test");
    }

    @Test
    void otpNotificationPublishesOtpEvent() {
        notificationService.otpNotification(user(), "654321");

        verify(notificationPublisher).publish(
                org.mockito.ArgumentMatchers.eq(NotificationType.OTP), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("otp", "654321")
                .containsEntry("email", "user@example.com")
                .containsKey("expiresInMinutes");
    }

    @Test
    void passwordResetNotificationPublishesResetEvent() {
        notificationService.passwordResetNotification(user());

        verify(notificationPublisher).publish(
                org.mockito.ArgumentMatchers.eq(NotificationType.PASSWORD_RESET_SUCCESS), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("email", "user@example.com")
                .containsKey("resetAt");
    }
}
