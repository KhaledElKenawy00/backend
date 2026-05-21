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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailIdempotencyServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private EmailIdempotencyService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dedupPrefix", "notif:processed:");
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void firstClaimSucceeds() {
        when(valueOps.setIfAbsent(eq("notif:processed:event-1"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        assertThat(service.tryClaim("event-1")).isTrue();
    }

    @Test
    void secondClaimWithSameEventIdFails() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertThat(service.tryClaim("event-1")).isFalse();
    }

    @Test
    void redisReturningNullIsTreatedAsAlreadyClaimed() {
        // Defensive: Redis returning null (transport hiccup) should not
        // accidentally let a duplicate through.
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(null);

        assertThat(service.tryClaim("event-1")).isFalse();
    }

    @Test
    void nullEventIdAlwaysPasses() {
        assertThat(service.tryClaim(null)).isTrue();
        verify(valueOps, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void blankEventIdAlwaysPasses() {
        assertThat(service.tryClaim("")).isTrue();
        assertThat(service.tryClaim("   ")).isTrue();
        verify(valueOps, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void ttlIs24Hours() {
        service.tryClaim("event-1");
        verify(valueOps).setIfAbsent(anyString(), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void keyUsesConfiguredPrefix() {
        service.tryClaim("xyz");
        verify(valueOps).setIfAbsent(eq("notif:processed:xyz"), anyString(), any(Duration.class));
    }

    @Test
    void releaseDeletesKey() {
        service.release("event-1");
        verify(redis).delete("notif:processed:event-1");
    }

    @Test
    void releaseIsSilentForNullOrBlank() {
        service.release(null);
        service.release("");
        service.release("   ");
        verify(redis, never()).delete(anyString());
    }

    @Test
    void releaseSwallowsRedisFailureSoOriginalErrorIsNotMasked() {
        when(redis.delete(anyString())).thenThrow(new RuntimeException("redis down"));
        // Must not throw - releasing is best-effort
        service.release("event-1");
    }
}
