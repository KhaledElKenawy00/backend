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
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoomPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public void participantJoined(String roomId, ParticipantResponse participant) {
        send(roomId, WebSocketEvent.of(WebSocketEvent.PARTICIPANT_JOINED, participant));
    }

    public void participantLeft(String roomId, Integer userId) {
        send(roomId, WebSocketEvent.of(WebSocketEvent.PARTICIPANT_LEFT, Map.of("userId", userId)));
    }

    public void stateChanged(String roomId, ParticipantResponse participant) {
        send(roomId, WebSocketEvent.of(WebSocketEvent.STATE_CHANGED, participant));
    }

    public void roomUpdated(String roomId, RoomResponse room) {
        send(roomId, WebSocketEvent.of(WebSocketEvent.ROOM_UPDATED, room));
    }

    public void roomClosed(String roomId) {
        send(roomId, WebSocketEvent.of(WebSocketEvent.ROOM_CLOSED, Map.of("roomId", roomId)));
    }

    private void send(String roomId, WebSocketEvent<?> event) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, event);
    }
}
