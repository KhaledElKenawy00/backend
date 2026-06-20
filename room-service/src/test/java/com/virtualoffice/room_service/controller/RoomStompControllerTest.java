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
package com.virtualoffice.room_service.controller;

import com.virtualoffice.room_service.dto.request.StompHeartbeat;
import com.virtualoffice.room_service.dto.request.StompRoomStateEvent;
import com.virtualoffice.room_service.dto.response.ParticipantResponse;
import com.virtualoffice.room_service.dto.response.WebSocketEvent;
import com.virtualoffice.room_service.service.PresenceService;
import com.virtualoffice.room_service.service.RoomPushService;
import com.virtualoffice.room_service.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomStompControllerTest {

    @Mock
    private RoomService roomService;

    @Mock
    private PresenceService presenceService;

    @Mock
    private RoomPushService roomPushService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoomStompController controller;

    private SimpMessageHeaderAccessor accessor;

    @BeforeEach
    void setUp() {
        accessor = SimpMessageHeaderAccessor.create();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", 10);
        accessor.setSessionAttributes(attrs);
        accessor.setSessionId("session-1");
    }

    @Test
    void stateShouldBroadcastWhenMember() {
        when(roomService.isMember("r1", 10)).thenReturn(true);
        ParticipantResponse updated = ParticipantResponse.builder().userId(10).muted(true).build();
        when(presenceService.updateState("r1", 10, true, false, false)).thenReturn(updated);

        controller.handleState(StompRoomStateEvent.builder().roomId("r1").muted(true).build(), accessor);

        verify(roomPushService).stateChanged("r1", updated);
    }

    @Test
    void stateShouldDropWhenNotMember() {
        when(roomService.isMember("r1", 10)).thenReturn(false);

        controller.handleState(StompRoomStateEvent.builder().roomId("r1").muted(true).build(), accessor);

        verify(presenceService, never()).updateState(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(roomPushService, never()).stateChanged(any(), any());
    }

    @Test
    void stateShouldSendErrorWhenRoomIdMissing() {
        controller.handleState(StompRoomStateEvent.builder().roomId(null).muted(true).build(), accessor);

        verify(messagingTemplate).convertAndSendToUser(eq("session-1"), eq("/queue/errors"), any(WebSocketEvent.class));
        verifyNoInteractions(presenceService);
    }

    @Test
    void stateShouldSendErrorWhenNoUserId() {
        SimpMessageHeaderAccessor noUser = SimpMessageHeaderAccessor.create();
        noUser.setSessionAttributes(new HashMap<>());
        noUser.setSessionId("session-2");

        controller.handleState(StompRoomStateEvent.builder().roomId("r1").build(), noUser);

        verify(messagingTemplate).convertAndSendToUser(eq("session-2"), eq("/queue/errors"), any(WebSocketEvent.class));
    }

    @Test
    void heartbeatShouldRefreshPresence() {
        controller.handleHeartbeat(StompHeartbeat.builder().roomId("r1").build(), accessor);

        verify(presenceService).heartbeat("r1", 10);
    }

    @Test
    void heartbeatShouldIgnoreWhenNoUserId() {
        SimpMessageHeaderAccessor noUser = SimpMessageHeaderAccessor.create();
        noUser.setSessionAttributes(new HashMap<>());

        controller.handleHeartbeat(StompHeartbeat.builder().roomId("r1").build(), noUser);

        verifyNoInteractions(presenceService);
    }
}
