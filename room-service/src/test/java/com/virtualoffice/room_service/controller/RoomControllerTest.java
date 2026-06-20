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

import com.virtualoffice.room_service.dto.request.AddMemberRequest;
import com.virtualoffice.room_service.dto.request.CreateRoomRequest;
import com.virtualoffice.room_service.dto.request.UpdateRoomRequest;
import com.virtualoffice.room_service.dto.response.PaginatedResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @Mock
    private RoomPushService roomPushService;

    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private RoomController controller;

    private HttpServletRequest mockRequest(String userId, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    @Test
    void shouldCreateRoomReturning201() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");
        CreateRoomRequest req = CreateRoomRequest.builder().name("Standup").workspaceId(1).build();
        RoomResponse expected = RoomResponse.builder().id("r1").name("Standup").channelId("c1").build();
        when(roomService.createRoom(any(), eq(10))).thenReturn(expected);

        ResponseEntity<RoomResponse> response = controller.createRoom(req, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void shouldListRooms() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");
        PaginatedResponse<RoomResponse> expected = PaginatedResponse.<RoomResponse>builder()
                .content(List.of()).currentPage(1).totalElements(0).totalPages(0).build();
        when(roomService.getRooms(1, 10, 1, 20)).thenReturn(expected);

        ResponseEntity<PaginatedResponse<RoomResponse>> response = controller.getRooms(1, 1, 20, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void shouldGetRoom() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");
        RoomResponse expected = RoomResponse.builder().id("r1").name("Standup").build();
        when(roomService.getRoom("r1", 10)).thenReturn(expected);

        ResponseEntity<RoomResponse> response = controller.getRoom("r1", httpRequest);

        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void shouldUpdateRoom() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");
        UpdateRoomRequest req = UpdateRoomRequest.builder().name("Renamed").build();
        RoomResponse expected = RoomResponse.builder().id("r1").name("Renamed").build();
        when(roomService.updateRoom(eq("r1"), any(), eq(10))).thenReturn(expected);

        ResponseEntity<RoomResponse> response = controller.updateRoom("r1", req, httpRequest);

        assertThat(response.getBody()).isEqualTo(expected);
        verify(roomPushService).roomUpdated(eq("r1"), eq(expected));
    }

    @Test
    void shouldDeleteRoom() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");

        ResponseEntity<Void> response = controller.deleteRoom("r1", httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(roomService).deleteRoom("r1", 10);
        verify(presenceService).clearRoom("r1");
        verify(roomPushService).roomClosed("r1");
    }

    @Test
    void shouldAddMember() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");

        ResponseEntity<Void> response = controller.addMember("r1", AddMemberRequest.builder().userId(30).build(), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(roomService).addMember("r1", 30, 10);
    }

    @Test
    void shouldRemoveMember() {
        HttpServletRequest httpRequest = mockRequest("10", "USER");

        ResponseEntity<Void> response = controller.removeMember("r1", 20, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(roomService).removeMember("r1", 20, 10);
    }
}
