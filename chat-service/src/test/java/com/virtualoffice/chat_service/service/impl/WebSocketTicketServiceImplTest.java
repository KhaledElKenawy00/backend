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
package com.virtualoffice.chat_service.service.impl;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketTicketServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private WebSocketTicketServiceImpl webSocketTicketService;

    // ────────────────────────────────────────
    // createTicket
    // ────────────────────────────────────────

    @Nested
    class CreateTicket {

        @Test
        void shouldReturnNonNullTicket() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String ticket = webSocketTicketService.createTicket(10, "USER");

            assertThat(ticket).isNotNull().isNotBlank();
        }

        @Test
        void shouldStoreTicketInRedisWithTtl() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String ticket = webSocketTicketService.createTicket(10, "USER");

            verify(valueOperations).set(
                    eq("ws-ticket:" + ticket),
                    eq("10:USER"),
                    eq(Duration.ofSeconds(60))
            );
        }

        @Test
        void shouldGenerateUniqueTicketsPerCall() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String ticket1 = webSocketTicketService.createTicket(10, "USER");
            String ticket2 = webSocketTicketService.createTicket(10, "USER");

            assertThat(ticket1).isNotEqualTo(ticket2);
        }

        @Test
        void shouldStoreUserIdAndRole() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            webSocketTicketService.createTicket(42, "ADMIN");

            verify(valueOperations).set(
                    anyString(),
                    eq("42:ADMIN"),
                    any(Duration.class)
            );
        }

        @Test
        void shouldDefaultNullRoleToUser() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            webSocketTicketService.createTicket(42, null);

            verify(valueOperations).set(
                    anyString(),
                    eq("42:USER"),
                    any(Duration.class)
            );
        }
    }

    // ────────────────────────────────────────
    // validateAndConsumeTicket
    // ────────────────────────────────────────

    @Nested
    class ValidateAndConsumeTicket {

        @Test
        void shouldReturnUserIdAndRoleForValidTicket() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getAndDelete("ws-ticket:valid-ticket")).thenReturn("10:USER");

            Map<String, Object> result = webSocketTicketService.validateAndConsumeTicket("valid-ticket");

            assertThat(result).isNotNull();
            assertThat(result.get("userId")).isEqualTo(10);
            assertThat(result.get("userRole")).isEqualTo("USER");
        }

        @Test
        void shouldReturnAdminRole() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getAndDelete("ws-ticket:admin-ticket")).thenReturn("42:ADMIN");

            Map<String, Object> result = webSocketTicketService.validateAndConsumeTicket("admin-ticket");

            assertThat(result).isNotNull();
            assertThat(result.get("userId")).isEqualTo(42);
            assertThat(result.get("userRole")).isEqualTo("ADMIN");
        }

        @Test
        void shouldReturnNullForInvalidTicket() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getAndDelete("ws-ticket:invalid-ticket")).thenReturn(null);

            Map<String, Object> result = webSocketTicketService.validateAndConsumeTicket("invalid-ticket");

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullForNullTicket() {
            Map<String, Object> result = webSocketTicketService.validateAndConsumeTicket(null);

            assertThat(result).isNull();
            // should not even touch Redis
            verifyNoInteractions(redisTemplate);
        }

        @Test
        void shouldDeleteTicketOnConsumption() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getAndDelete("ws-ticket:one-time")).thenReturn("10:USER");

            webSocketTicketService.validateAndConsumeTicket("one-time");

            // getAndDelete atomically gets and removes — this IS the consumption
            verify(valueOperations).getAndDelete("ws-ticket:one-time");
        }

        @Test
        void shouldNotReturnDataOnSecondUse() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            // first call succeeds
            when(valueOperations.getAndDelete("ws-ticket:used-ticket"))
                    .thenReturn("10:USER")   // first call
                    .thenReturn(null);       // second call — ticket already consumed

            Map<String, Object> first = webSocketTicketService.validateAndConsumeTicket("used-ticket");
            Map<String, Object> second = webSocketTicketService.validateAndConsumeTicket("used-ticket");

            assertThat(first).isNotNull();
            assertThat(first.get("userId")).isEqualTo(10);
            assertThat(second).isNull();
        }
    }
}
