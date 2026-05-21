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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Dedups outgoing emails by eventId. The in-app path relies on the unique
// index on Notification.eventId; emails have no equivalent backstop at SMTP,
// so we guard upstream via Redis SETNX with a 24h TTL.
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailIdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    @Value("${notifications.redis.dedup-prefix}")
    private String dedupPrefix;

    public boolean tryClaim(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        String key = dedupPrefix + eventId;
        Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", TTL);
        return Boolean.TRUE.equals(acquired);
    }

    // Drops the claim so a subsequent attempt can re-acquire it. Used by the
    // listener after a transient failure so Spring AMQP's in-process retry
    // can actually re-attempt the SMTP send. A failed release (Redis hiccup)
    // is logged but not propagated - we don't want to mask the original error.
    public void release(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            redis.delete(dedupPrefix + eventId);
        } catch (RuntimeException e) {
            log.warn("Failed to release dedup claim for {}: {}", eventId, e.getMessage());
        }
    }
}
