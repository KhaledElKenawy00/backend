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

import com.virtualoffice.room_service.client.WorkspaceClient;
import com.virtualoffice.room_service.client.Zone;
import com.virtualoffice.room_service.dto.request.PlayerPosition;
import com.virtualoffice.room_service.dto.response.VoiceGroupChange;
import com.virtualoffice.room_service.dto.response.WebSocketEvent;
import com.virtualoffice.room_service.service.ProximityService;
import com.virtualoffice.room_service.voice.VoiceGrouping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the in-memory position index per workspace and turns each snapshot into voice-channel
 * assignments via {@link VoiceGrouping}, broadcasting only the avatars whose channel changed.
 *
 * <p>Positions are ephemeral (never persisted). Updates for one workspace are serialised on a
 * per-workspace lock so concurrent batches cannot interleave the read-diff-write of its assignment.
 */
@Service
public class ProximityServiceImpl implements ProximityService {

    private final WorkspaceClient workspaceClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final int defaultRadius;

    private final Map<Integer, Map<Integer, String>> lastAssignments = new ConcurrentHashMap<>();
    private final Map<Integer, Object> locks = new ConcurrentHashMap<>();

    public ProximityServiceImpl(WorkspaceClient workspaceClient,
                                SimpMessagingTemplate messagingTemplate,
                                @Value("${room.voice.proximity-default-radius:150}") int defaultRadius) {
        this.workspaceClient = workspaceClient;
        this.messagingTemplate = messagingTemplate;
        this.defaultRadius = defaultRadius;
    }

    @Override
    public Map<Integer, String> updatePositions(int workspaceId, List<PlayerPosition> positions) {
        Map<Integer, int[]> snapshot = new LinkedHashMap<>();
        for (PlayerPosition p : positions) {
            snapshot.put(p.userId(), new int[]{p.x(), p.y()});
        }

        synchronized (locks.computeIfAbsent(workspaceId, k -> new Object())) {
            List<Zone> zones = workspaceClient.getZones(workspaceId);
            Map<Integer, String> assignment = VoiceGrouping.assign(workspaceId, zones, snapshot, defaultRadius);

            Map<Integer, String> previous = lastAssignments.getOrDefault(workspaceId, Map.of());
            List<VoiceGroupChange> changes = diff(previous, assignment);
            if (!changes.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/workspace/" + workspaceId + "/voice",
                        WebSocketEvent.of(WebSocketEvent.VOICE_GROUP_CHANGED, changes));
            }
            lastAssignments.put(workspaceId, assignment);
            return assignment;
        }
    }

    /** Avatars whose channel differs from the previous assignment (including leaving voice). */
    private List<VoiceGroupChange> diff(Map<Integer, String> previous, Map<Integer, String> current) {
        Set<Integer> affected = new HashSet<>(previous.keySet());
        affected.addAll(current.keySet());

        List<VoiceGroupChange> changes = new ArrayList<>();
        for (Integer userId : affected) {
            String now = current.get(userId);
            if (!Objects.equals(now, previous.get(userId))) {
                changes.add(new VoiceGroupChange(userId, now, peers(current, userId, now)));
            }
        }
        return changes;
    }

    private List<Integer> peers(Map<Integer, String> assignment, Integer userId, String channel) {
        if (channel == null) {
            return List.of();
        }
        List<Integer> peers = new ArrayList<>();
        for (Map.Entry<Integer, String> e : assignment.entrySet()) {
            if (channel.equals(e.getValue()) && !e.getKey().equals(userId)) {
                peers.add(e.getKey());
            }
        }
        return peers;
    }
}
