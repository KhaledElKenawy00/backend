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
import com.virtualoffice.room_service.client.WorkspaceRole;
import com.virtualoffice.room_service.dto.request.CreateRoomRequest;
import com.virtualoffice.room_service.dto.request.UpdateRoomRequest;
import com.virtualoffice.room_service.dto.response.PaginatedResponse;
import com.virtualoffice.room_service.dto.response.RoomResponse;
import com.virtualoffice.room_service.messaging.RoomChannelEventPublisher;
import com.virtualoffice.room_service.model.Room;
import com.virtualoffice.room_service.repository.RoomRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomChannelEventPublisher publisher;

    @Mock
    private WorkspaceClient workspaceClient;

    @InjectMocks
    private RoomServiceImpl roomService;

    private ObjectId roomId;
    private String channelId;
    private Room room;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(roomService, "agoraChannelPrefix", "room-");
        roomId = new ObjectId();
        channelId = new ObjectId().toHexString();
        room = Room.builder()
                .id(roomId)
                .workspaceId(1)
                .name("Standup")
                .channelId(channelId)
                .agoraChannelName("room-" + roomId.toHexString())
                .members(new ArrayList<>(List.of(10, 20)))
                .maxParticipants(25)
                .createdBy(10)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    class CreateRoom {

        @Test
        void shouldCreateRoomAndPublishCreateEvent() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .name("Standup").workspaceId(1).members(List.of(20)).build();

            RoomResponse response = roomService.createRoom(request, 10);

            assertThat(response.getName()).isEqualTo("Standup");
            assertThat(response.getWorkspaceId()).isEqualTo(1);
            assertThat(response.getChannelId()).isNotBlank();
            assertThat(response.getAgoraChannelName()).startsWith("room-");
            assertThat(response.getMembers()).containsExactlyInAnyOrder(10, 20);
            assertThat(response.getMaxParticipants()).isEqualTo(25);

            verify(roomRepository).save(any(Room.class));
            verify(publisher).publishCreate(eq(response.getChannelId()), eq(1), eq("Standup"), anyList());
            // creating a room requires active membership in the workspace
            verify(workspaceClient).requireRole(1, 10, WorkspaceRole.MEMBER);
        }

        @Test
        void shouldRejectCreationWhenNotAuthorizedInWorkspace() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .name("Standup").workspaceId(1).members(List.of(20)).build();
            doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "not a member"))
                    .when(workspaceClient).requireRole(1, 99, WorkspaceRole.MEMBER);

            assertThatThrownBy(() -> roomService.createRoom(request, 99))
                    .isInstanceOf(ResponseStatusException.class);

            verify(roomRepository, never()).save(any());
            verify(publisher, never()).publishCreate(any(), any(), any(), any());
        }

        @Test
        void shouldDefaultMembersToCreatorOnly() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .name("Solo").workspaceId(1).build();

            RoomResponse response = roomService.createRoom(request, 10);

            assertThat(response.getMembers()).containsExactly(10);
        }

        @Test
        void shouldReturn409OnDuplicateRoomName() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .name("Standup").workspaceId(1).build();
            when(roomRepository.save(any(Room.class))).thenThrow(new DuplicateKeyException("dup"));

            assertThatThrownBy(() -> roomService.createRoom(request, 10))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("already exists");
            verify(publisher, never()).publishCreate(any(), any(), any(), any());
        }

        @Test
        void shouldCompensateAndReturn503WhenPublishFails() {
            CreateRoomRequest request = CreateRoomRequest.builder()
                    .name("Standup").workspaceId(1).build();
            doThrow(new AmqpException("broker down"))
                    .when(publisher).publishCreate(any(), any(), any(), any());

            assertThatThrownBy(() -> roomService.createRoom(request, 10))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("failed to provision");
            verify(roomRepository).deleteById(any(ObjectId.class));
        }
    }

    @Nested
    class GetRooms {

        @Test
        void shouldReturnPaginatedRooms() {
            Page<Room> page = new PageImpl<>(List.of(room));
            when(roomRepository.findByWorkspaceIdAndMember(eq(1), eq(10), any(Pageable.class))).thenReturn(page);

            PaginatedResponse<RoomResponse> result = roomService.getRooms(1, 10, 1, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Standup");
            assertThat(result.getCurrentPage()).isEqualTo(1);
        }
    }

    @Nested
    class GetRoom {

        @Test
        void shouldReturnRoomForMember() {
            when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

            RoomResponse response = roomService.getRoom(roomId.toHexString(), 10);

            assertThat(response.getId()).isEqualTo(roomId.toHexString());
        }

        @Test
        void shouldThrow403ForNonMember() {
            when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> roomService.getRoom(roomId.toHexString(), 99))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        void shouldThrow404WhenNotFound() {
            when(roomRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roomService.getRoom(new ObjectId().toHexString(), 10))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("room not found");
        }
    }

    @Nested
    class UpdateRoom {

        @Test
        void shouldRenameRoom() {
            when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

            RoomResponse response = roomService.updateRoom(roomId.toHexString(),
                    UpdateRoomRequest.builder().name("Renamed").build(), 10);

            assertThat(response.getName()).isEqualTo("Renamed");
            verify(roomRepository).save(room);
        }
    }

    @Nested
    class DeleteRoom {

        @Test
        void shouldDeleteRoomAndPublishDelete() {
            when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

            roomService.deleteRoom(roomId.toHexString(), 10);

            verify(roomRepository).deleteById(roomId);
            verify(publisher).publishDelete(channelId);
        }
    }

    @Nested
    class Members {

        @Test
        void shouldAddMemberAndPublish() {
            when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

            roomService.addMember(roomId.toHexString(), 30, 10);

            verify(roomRepository).addMember(eq(roomId), eq(30), any(Instant.class));
            verify(publisher).publishAddMember(channelId, 30);
        }

        @Test
        void shouldRejectAddMemberByNonMember() {
            when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> roomService.addMember(roomId.toHexString(), 30, 99))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not a member");
            verify(roomRepository, never()).addMember(any(), anyInt(), any());
        }

        @Test
        void shouldRemoveMemberAndPublish() {
            when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

            roomService.removeMember(roomId.toHexString(), 20, 10);

            verify(roomRepository).removeMember(eq(roomId), eq(20), any(Instant.class));
            verify(publisher).publishRemoveMember(channelId, 20);
        }
    }

    @Nested
    class IsMember {

        @Test
        void shouldReturnTrueForMember() {
            when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
            assertThat(roomService.isMember(roomId.toHexString(), 10)).isTrue();
        }

        @Test
        void shouldReturnFalseForNonMember() {
            when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
            assertThat(roomService.isMember(roomId.toHexString(), 99)).isFalse();
        }

        @Test
        void shouldReturnFalseWhenRoomNotFound() {
            when(roomRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());
            assertThat(roomService.isMember(new ObjectId().toHexString(), 10)).isFalse();
        }
    }
}
