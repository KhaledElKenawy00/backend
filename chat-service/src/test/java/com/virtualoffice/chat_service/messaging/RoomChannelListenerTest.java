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
package com.virtualoffice.chat_service.messaging;

import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChannelType;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomChannelListenerTest {

    @Mock
    private ChannelRepository channelRepository;

    @InjectMocks
    private RoomChannelListener listener;

    @Test
    void shouldCreateRoomChannelWhenAbsent() {
        ObjectId channelId = new ObjectId();
        when(channelRepository.existsById(channelId)).thenReturn(false);

        RoomChannelEvent event = RoomChannelEvent.builder()
                .eventId("e1")
                .type(RoomChannelEventType.ROOM_CHANNEL_CREATE)
                .channelId(channelId.toHexString())
                .workspaceId(7)
                .name("Standup Room")
                .members(List.of(1, 2, 42))
                .userId(1)
                .build();

        listener.handle(event);

        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(captor.capture());
        Channel saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(channelId);
        assertThat(saved.getType()).isEqualTo(ChannelType.ROOM);
        assertThat(saved.getName()).isNull();
        assertThat(saved.getWorkspaceId()).isEqualTo(7);
        assertThat(saved.getMembers()).containsExactlyInAnyOrder(1, 2, 42);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldSkipCreateWhenChannelAlreadyExists() {
        ObjectId channelId = new ObjectId();
        when(channelRepository.existsById(channelId)).thenReturn(true);

        RoomChannelEvent event = RoomChannelEvent.builder()
                .eventId("e1")
                .type(RoomChannelEventType.ROOM_CHANNEL_CREATE)
                .channelId(channelId.toHexString())
                .workspaceId(7)
                .members(List.of(1))
                .build();

        listener.handle(event);

        verify(channelRepository, never()).save(any());
    }

    @Test
    void shouldHandleNullMembersOnCreate() {
        ObjectId channelId = new ObjectId();
        when(channelRepository.existsById(channelId)).thenReturn(false);

        RoomChannelEvent event = RoomChannelEvent.builder()
                .type(RoomChannelEventType.ROOM_CHANNEL_CREATE)
                .channelId(channelId.toHexString())
                .workspaceId(7)
                .members(null)
                .build();

        listener.handle(event);

        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(captor.capture());
        assertThat(captor.getValue().getMembers()).isEmpty();
    }

    @Test
    void shouldDeleteRoomChannel() {
        ObjectId channelId = new ObjectId();

        RoomChannelEvent event = RoomChannelEvent.builder()
                .type(RoomChannelEventType.ROOM_CHANNEL_DELETE)
                .channelId(channelId.toHexString())
                .build();

        listener.handle(event);

        verify(channelRepository).deleteById(channelId);
        verify(channelRepository, never()).save(any());
    }

    @Test
    void shouldAddMember() {
        ObjectId channelId = new ObjectId();

        RoomChannelEvent event = RoomChannelEvent.builder()
                .type(RoomChannelEventType.ROOM_CHANNEL_ADD_MEMBER)
                .channelId(channelId.toHexString())
                .userId(99)
                .build();

        listener.handle(event);

        verify(channelRepository).addMember(eq(channelId), eq(99), any(Instant.class));
    }

    @Test
    void shouldRemoveMember() {
        ObjectId channelId = new ObjectId();

        RoomChannelEvent event = RoomChannelEvent.builder()
                .type(RoomChannelEventType.ROOM_CHANNEL_REMOVE_MEMBER)
                .channelId(channelId.toHexString())
                .userId(99)
                .build();

        listener.handle(event);

        verify(channelRepository).removeMember(eq(channelId), eq(99), any(Instant.class));
    }

    @Test
    void shouldDropEventWithNullType() {
        RoomChannelEvent event = RoomChannelEvent.builder()
                .type(null)
                .channelId(new ObjectId().toHexString())
                .build();

        listener.handle(event);

        verifyNoInteractions(channelRepository);
    }

    @Test
    void shouldDropEventWithInvalidChannelId() {
        RoomChannelEvent event = RoomChannelEvent.builder()
                .type(RoomChannelEventType.ROOM_CHANNEL_CREATE)
                .channelId("not-a-valid-objectid")
                .workspaceId(7)
                .members(List.of(1))
                .build();

        listener.handle(event);

        verifyNoInteractions(channelRepository);
    }

    @Test
    void shouldDropAddMemberWithNullUserId() {
        RoomChannelEvent event = RoomChannelEvent.builder()
                .type(RoomChannelEventType.ROOM_CHANNEL_ADD_MEMBER)
                .channelId(new ObjectId().toHexString())
                .userId(null)
                .build();

        listener.handle(event);

        verify(channelRepository, never()).addMember(any(), any(), any());
    }

    @Test
    void shouldDropRemoveMemberWithNullUserId() {
        RoomChannelEvent event = RoomChannelEvent.builder()
                .type(RoomChannelEventType.ROOM_CHANNEL_REMOVE_MEMBER)
                .channelId(new ObjectId().toHexString())
                .userId(null)
                .build();

        listener.handle(event);

        verify(channelRepository, never()).removeMember(any(), any(), any());
    }
}
