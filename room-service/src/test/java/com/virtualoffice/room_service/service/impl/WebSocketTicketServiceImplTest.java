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
package com.virtualoffice.room_service.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketTicketServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private WebSocketTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "ticketPrefix", "room:ws-ticket:");
        ReflectionTestUtils.setField(service, "ticketTtlSeconds", 60L);
    }

    @Test
    void createTicketShouldStoreUserIdWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        String ticket = service.createTicket(10);

        assertThat(ticket).isNotBlank();
        verify(valueOps).set(eq("room:ws-ticket:" + ticket), eq("10"), any(Duration.class));
    }

    @Test
    void validateShouldReturnUserId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("room:ws-ticket:abc")).thenReturn("10");

        assertThat(service.validateAndConsumeTicket("abc")).isEqualTo(10);
    }

    @Test
    void validateShouldReturnNullForNullOrBlank() {
        assertThat(service.validateAndConsumeTicket(null)).isNull();
        assertThat(service.validateAndConsumeTicket("  ")).isNull();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void validateShouldReturnNullForMissingTicket() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("room:ws-ticket:gone")).thenReturn(null);

        assertThat(service.validateAndConsumeTicket("gone")).isNull();
    }

    @Test
    void validateShouldReturnNullForNonNumericValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("room:ws-ticket:bad")).thenReturn("not-a-number");

        assertThat(service.validateAndConsumeTicket("bad")).isNull();
    }
}
