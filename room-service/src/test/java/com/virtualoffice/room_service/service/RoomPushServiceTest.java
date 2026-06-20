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
package com.virtualoffice.room_service.service;

import com.virtualoffice.room_service.dto.response.ParticipantResponse;
import com.virtualoffice.room_service.dto.response.RoomResponse;
import com.virtualoffice.room_service.dto.response.WebSocketEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoomPushServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoomPushService roomPushService;

    private WebSocketEvent<?> captureFor(String roomId) {
        ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId), captor.capture());
        return captor.getValue();
    }

    @Test
    void participantJoinedBroadcastsToRoomTopic() {
        ParticipantResponse p = ParticipantResponse.builder().userId(10).build();
        roomPushService.participantJoined("r1", p);

        WebSocketEvent<?> event = captureFor("r1");
        assertThat(event.getAction()).isEqualTo("PARTICIPANT_JOINED");
        assertThat(event.getPayload()).isEqualTo(p);
    }

    @Test
    void participantLeftBroadcastsUserId() {
        roomPushService.participantLeft("r1", 10);

        WebSocketEvent<?> event = captureFor("r1");
        assertThat(event.getAction()).isEqualTo("PARTICIPANT_LEFT");
        assertThat(((Map<?, ?>) event.getPayload()).get("userId")).isEqualTo(10);
    }

    @Test
    void stateChangedBroadcastsParticipant() {
        ParticipantResponse p = ParticipantResponse.builder().userId(10).muted(true).build();
        roomPushService.stateChanged("r1", p);

        WebSocketEvent<?> event = captureFor("r1");
        assertThat(event.getAction()).isEqualTo("STATE_CHANGED");
        assertThat(event.getPayload()).isEqualTo(p);
    }

    @Test
    void roomUpdatedBroadcastsRoom() {
        RoomResponse room = RoomResponse.builder().id("r1").name("Renamed").build();
        roomPushService.roomUpdated("r1", room);

        WebSocketEvent<?> event = captureFor("r1");
        assertThat(event.getAction()).isEqualTo("ROOM_UPDATED");
        assertThat(event.getPayload()).isEqualTo(room);
    }

    @Test
    void roomClosedBroadcastsRoomId() {
        roomPushService.roomClosed("r1");

        WebSocketEvent<?> event = captureFor("r1");
        assertThat(event.getAction()).isEqualTo("ROOM_CLOSED");
        assertThat(((Map<?, ?>) event.getPayload()).get("roomId")).isEqualTo("r1");
    }
}
