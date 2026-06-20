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

import com.virtualoffice.room_service.dto.response.ParticipantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceImplTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    private PresenceServiceImpl presence;

    @BeforeEach
    void setUp() {
        presence = new PresenceServiceImpl(redis);
        ReflectionTestUtils.setField(presence, "aliveTtlSeconds", 30L);
    }

    private String csv() {
        return "false,false,false," + Instant.now().toEpochMilli();
    }

    @Test
    void joinShouldAddParticipantAndSetAlive() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(1L);
        when(redis.opsForValue()).thenReturn(valueOps);

        ParticipantResponse result = presence.join("rid", 10, 25);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(10);
        verify(valueOps).set(eq("room:rid:alive:10"), eq("1"), any(Duration.class));
    }

    @Test
    void joinShouldThrow409WhenFull() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(0L);

        assertThatThrownBy(() -> presence.join("rid", 10, 25))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("room is full");
    }

    @Test
    void joinShouldReturnNullWhenAlreadyPresent() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(2L);
        when(redis.opsForValue()).thenReturn(valueOps);

        ParticipantResponse result = presence.join("rid", 10, 25);

        assertThat(result).isNull();
    }

    @Test
    void leaveShouldRemoveParticipant() {
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.delete("room:rid:participants", "10")).thenReturn(1L);

        boolean removed = presence.leave("rid", 10);

        assertThat(removed).isTrue();
        verify(redis).delete("room:rid:alive:10");
    }

    @Test
    void updateStateShouldReturnNullWhenAbsent() {
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.get("room:rid:participants", "10")).thenReturn(null);

        ParticipantResponse result = presence.updateState("rid", 10, true, true, false);

        assertThat(result).isNull();
    }

    @Test
    void updateStateShouldUpdateFlagsWhenPresent() {
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.get("room:rid:participants", "10")).thenReturn(csv());
        when(redis.opsForValue()).thenReturn(valueOps);

        ParticipantResponse result = presence.updateState("rid", 10, true, true, true);

        assertThat(result).isNotNull();
        assertThat(result.isMuted()).isTrue();
        assertThat(result.isCameraOn()).isTrue();
        assertThat(result.isScreenSharing()).isTrue();
        verify(hashOps).put(eq("room:rid:participants"), eq("10"), anyString());
    }

    @Test
    void listParticipantsShouldFilterAndReapStale() {
        when(redis.opsForHash()).thenReturn(hashOps);
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("10", csv());
        entries.put("20", csv());
        when(hashOps.entries("room:rid:participants")).thenReturn(entries);
        when(redis.hasKey("room:rid:alive:10")).thenReturn(true);
        when(redis.hasKey("room:rid:alive:20")).thenReturn(false);

        List<ParticipantResponse> live = presence.listParticipants("rid");

        assertThat(live).hasSize(1);
        assertThat(live.get(0).getUserId()).isEqualTo(10);
        verify(hashOps).delete("room:rid:participants", "20");
    }

    @Test
    void clearRoomShouldDeleteParticipantsHash() {
        presence.clearRoom("rid");
        verify(redis).delete("room:rid:participants");
    }
}
