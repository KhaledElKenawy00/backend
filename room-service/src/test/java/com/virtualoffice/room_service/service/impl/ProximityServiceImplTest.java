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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProximityServiceImplTest {

    private static final int WID = 7;
    private static final String VOICE_TOPIC = "/topic/workspace/7/voice";

    @Mock
    private WorkspaceClient workspaceClient;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ProximityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProximityServiceImpl(workspaceClient, messagingTemplate, 100);
    }

    private static PlayerPosition at(int userId, int x, int y) {
        return new PlayerPosition(userId, x, y);
    }

    @Test
    void formsProximityGroupAndBroadcastsTheChange() {
        when(workspaceClient.getZones(WID)).thenReturn(List.of());

        Map<Integer, String> assignment = service.updatePositions(WID, List.of(at(10, 0, 0), at(20, 30, 0)));

        assertThat(assignment).containsEntry(10, "prox-7-10").containsEntry(20, "prox-7-10");

        WebSocketEvent<?> event = captureBroadcast();
        assertThat(event.getAction()).isEqualTo(WebSocketEvent.VOICE_GROUP_CHANGED);
        @SuppressWarnings("unchecked")
        List<VoiceGroupChange> changes = (List<VoiceGroupChange>) event.getPayload();
        assertThat(changes).extracting(VoiceGroupChange::userId).containsExactlyInAnyOrder(10, 20);
        assertThat(changes).allSatisfy(c -> assertThat(c.channel()).isEqualTo("prox-7-10"));
    }

    @Test
    void assignsZoneVoiceWhenInsideAMeetingRoom() {
        when(workspaceClient.getZones(WID))
                .thenReturn(List.of(new Zone(1L, "MEETING_ROOM", "Sync", 0, 0, 100, 100, "voice-sync", null)));

        Map<Integer, String> assignment = service.updatePositions(WID, List.of(at(10, 50, 50)));

        assertThat(assignment).containsEntry(10, "voice-sync");
        verify(messagingTemplate).convertAndSend(eq(VOICE_TOPIC), any(Object.class));
    }

    @Test
    void doesNotBroadcastWhenAssignmentIsUnchanged() {
        when(workspaceClient.getZones(WID)).thenReturn(List.of());
        List<PlayerPosition> snapshot = List.of(at(10, 0, 0), at(20, 30, 0));

        service.updatePositions(WID, snapshot);
        service.updatePositions(WID, snapshot); // identical -> no new change

        verify(messagingTemplate, times(1)).convertAndSend(eq(VOICE_TOPIC), any(Object.class));
    }

    @Test
    void broadcastsDepartureWhenAvatarsMoveApart() {
        when(workspaceClient.getZones(WID)).thenReturn(List.of());
        service.updatePositions(WID, List.of(at(10, 0, 0), at(20, 30, 0))); // grouped

        service.updatePositions(WID, List.of(at(10, 0, 0), at(20, 5000, 0))); // now far apart

        // second broadcast tells both they left the channel (channel == null)
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq(VOICE_TOPIC), payload.capture());
        @SuppressWarnings("unchecked")
        List<VoiceGroupChange> last = (List<VoiceGroupChange>) ((WebSocketEvent<?>) payload.getValue()).getPayload();
        assertThat(last).extracting(VoiceGroupChange::userId).containsExactlyInAnyOrder(10, 20);
        assertThat(last).allSatisfy(c -> assertThat(c.channel()).isNull());
    }

    @Test
    void emptySnapshotWithNoPriorStateBroadcastsNothing() {
        when(workspaceClient.getZones(WID)).thenReturn(List.of());

        Map<Integer, String> assignment = service.updatePositions(WID, List.of());

        assertThat(assignment).isEmpty();
        verify(messagingTemplate, never()).convertAndSend(eq(VOICE_TOPIC), any(Object.class));
    }

    private WebSocketEvent<?> captureBroadcast() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(VOICE_TOPIC), payload.capture());
        return (WebSocketEvent<?>) payload.getValue();
    }
}
