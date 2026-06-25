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

import com.virtualoffice.room_service.dto.response.JoinRoomResponse;
import com.virtualoffice.room_service.dto.response.ParticipantResponse;
import com.virtualoffice.room_service.dto.response.RoomResponse;
import com.virtualoffice.room_service.service.PresenceService;
import com.virtualoffice.room_service.service.RoomPushService;
import com.virtualoffice.room_service.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomSessionControllerTest {

    @Mock
    private RoomService roomService;

    @Mock
    private PresenceService presenceService;

    @Mock
    private RoomPushService roomPushService;

    @InjectMocks
    private RoomSessionController controller;

    private HttpServletRequest mockRequest(String userId, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    private RoomResponse room() {
        return RoomResponse.builder().id("r1").name("Standup")
                .agoraChannelName("room-r1").maxParticipants(25).build();
    }

    @Test
    void joinShouldReturnAgoraChannelAndParticipantsAndBroadcast() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");
        when(roomService.ensureMemberAndGet("r1", 10)).thenReturn(room());
        ParticipantResponse joined = ParticipantResponse.builder().userId(10).build();
        when(presenceService.join("r1", 10, 25)).thenReturn(joined);
        when(presenceService.listParticipants("r1")).thenReturn(List.of(joined));

        ResponseEntity<JoinRoomResponse> response = controller.join("r1", httpRequest);

        assertThat(response.getBody().getAgoraChannelName()).isEqualTo("room-r1");
        assertThat(response.getBody().getParticipants()).hasSize(1);
        verify(roomPushService).participantJoined("r1", joined);
    }

    @Test
    void joinShouldNotBroadcastWhenAlreadyPresent() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");
        when(roomService.ensureMemberAndGet("r1", 10)).thenReturn(room());
        when(presenceService.join("r1", 10, 25)).thenReturn(null);
        when(presenceService.listParticipants("r1")).thenReturn(List.of());

        controller.join("r1", httpRequest);

        verify(roomPushService, never()).participantJoined(any(), any());
    }

    @Test
    void leaveShouldBroadcastWhenRemoved() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");
        when(presenceService.leave("r1", 10)).thenReturn(true);

        controller.leave("r1", httpRequest);

        verify(roomPushService).participantLeft("r1", 10);
    }

    @Test
    void leaveShouldNotBroadcastWhenNotPresent() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");
        when(presenceService.leave("r1", 10)).thenReturn(false);

        controller.leave("r1", httpRequest);

        verify(roomPushService, never()).participantLeft(any(), any());
    }

    @Test
    void participantsShouldGateOnMembershipAndReturnList() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");
        when(roomService.getRoom("r1", 10)).thenReturn(room());
        when(presenceService.listParticipants("r1"))
                .thenReturn(List.of(ParticipantResponse.builder().userId(10).build()));

        ResponseEntity<List<ParticipantResponse>> response = controller.participants("r1", httpRequest);

        assertThat(response.getBody()).hasSize(1);
        verify(roomService).getRoom("r1", 10);
    }
}
