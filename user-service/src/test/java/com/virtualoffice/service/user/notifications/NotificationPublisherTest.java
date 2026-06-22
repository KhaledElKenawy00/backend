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
package com.virtualoffice.service.user.notifications;

import com.virtualoffice.service.user.domain.enumuration.NotificationType;
import com.virtualoffice.service.user.dto.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "exchange", "notifications.exchange");
        ReflectionTestUtils.setField(publisher, "routingKey", "routing.key");
    }

    @Test
    void publishSendsEventToExchangeAndRoutingKey() {
        publisher.publish(NotificationType.OTP, Map.of("email", "user@example.com"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("notifications.exchange"), eq("routing.key"), captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo(NotificationType.OTP);
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getPayload()).containsEntry("email", "user@example.com");
    }

    @Test
    void publishSwallowsBrokerFailures() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(eq("notifications.exchange"), eq("routing.key"), any(Object.class));

        assertThatCode(() -> publisher.publish(NotificationType.SIGNUP_SUCCESS, Map.of("email", "user@example.com")))
                .doesNotThrowAnyException();
    }
}
