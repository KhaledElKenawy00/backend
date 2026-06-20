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
import com.virtualoffice.room_service.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    private final StringRedisTemplate redis;

    @Value("${room.presence.alive-ttl-seconds}")
    private long aliveTtlSeconds;

    private static final DefaultRedisScript<Long> JOIN_SCRIPT;
    static {
        JOIN_SCRIPT = new DefaultRedisScript<>();
        JOIN_SCRIPT.setScriptText(
                "if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 1 then return 2 end " +
                "if redis.call('HLEN', KEYS[1]) >= tonumber(ARGV[3]) then return 0 end " +
                "redis.call('HSET', KEYS[1], ARGV[1], ARGV[2]) " +
                "return 1"
        );
        JOIN_SCRIPT.setResultType(Long.class);
    }

    private String participantsKey(String roomId) {
        return "room:" + roomId + ":participants";
    }

    private String aliveKey(String roomId, Integer userId) {
        return "room:" + roomId + ":alive:" + userId;
    }

    @Override
    public ParticipantResponse join(String roomId, Integer userId, int maxParticipants) {
        ParticipantResponse participant = ParticipantResponse.builder()
                .userId(userId)
                .muted(false)
                .cameraOn(false)
                .screenSharing(false)
                .joinedAt(Instant.now())
                .build();

        Long result = redis.execute(
                JOIN_SCRIPT,
                Collections.singletonList(participantsKey(roomId)),
                String.valueOf(userId), encode(participant), String.valueOf(maxParticipants));

        if (result != null && result == 0L) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "room is full");
        }
        refreshAlive(roomId, userId);
        return (result != null && result == 1L) ? participant : null;
    }

    @Override
    public boolean leave(String roomId, Integer userId) {
        Long removed = redis.opsForHash().delete(participantsKey(roomId), String.valueOf(userId));
        redis.delete(aliveKey(roomId, userId));
        return removed != null && removed > 0;
    }

    @Override
    public ParticipantResponse updateState(String roomId, Integer userId, boolean muted, boolean cameraOn, boolean screenSharing) {
        Object existing = redis.opsForHash().get(participantsKey(roomId), String.valueOf(userId));
        if (existing == null) {
            return null;
        }
        ParticipantResponse participant = decode(userId, (String) existing);
        participant.setMuted(muted);
        participant.setCameraOn(cameraOn);
        participant.setScreenSharing(screenSharing);
        redis.opsForHash().put(participantsKey(roomId), String.valueOf(userId), encode(participant));
        refreshAlive(roomId, userId);
        return participant;
    }

    @Override
    public void heartbeat(String roomId, Integer userId) {
        if (Boolean.TRUE.equals(redis.opsForHash().hasKey(participantsKey(roomId), String.valueOf(userId)))) {
            refreshAlive(roomId, userId);
        }
    }

    @Override
    public List<ParticipantResponse> listParticipants(String roomId) {
        Map<Object, Object> entries = redis.opsForHash().entries(participantsKey(roomId));
        List<ParticipantResponse> live = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String userIdStr = (String) entry.getKey();
            if (Boolean.TRUE.equals(redis.hasKey(aliveKey(roomId, Integer.valueOf(userIdStr))))) {
                live.add(decode(Integer.valueOf(userIdStr), (String) entry.getValue()));
            } else {
                redis.opsForHash().delete(participantsKey(roomId), userIdStr);
            }
        }
        return live;
    }

    @Override
    public void clearRoom(String roomId) {
        redis.delete(participantsKey(roomId));
    }

    private void refreshAlive(String roomId, Integer userId) {
        redis.opsForValue().set(aliveKey(roomId, userId), "1", Duration.ofSeconds(aliveTtlSeconds));
    }

    private String encode(ParticipantResponse p) {
        return p.isMuted() + "," + p.isCameraOn() + "," + p.isScreenSharing() + "," + p.getJoinedAt().toEpochMilli();
    }

    private ParticipantResponse decode(Integer userId, String value) {
        String[] parts = value.split(",", 4);
        return ParticipantResponse.builder()
                .userId(userId)
                .muted(Boolean.parseBoolean(parts[0]))
                .cameraOn(Boolean.parseBoolean(parts[1]))
                .screenSharing(Boolean.parseBoolean(parts[2]))
                .joinedAt(Instant.ofEpochMilli(Long.parseLong(parts[3])))
                .build();
    }
}
