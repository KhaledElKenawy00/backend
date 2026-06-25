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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceChannelListenerTest {

    @Mock
    private ChannelRepository channelRepository;

    @InjectMocks
    private WorkspaceChannelListener listener;

    private WorkspaceChannelEvent.WorkspaceChannelEventBuilder event(WorkspaceChannelEventType type) {
        return WorkspaceChannelEvent.builder().eventId("e1").type(type).workspaceId(42);
    }

    @Test
    void createProvisionsCanonicalGroupChannelWhenAbsent() {
        when(channelRepository.findCanonicalByWorkspaceId(42)).thenReturn(Optional.empty());

        listener.handle(event(WorkspaceChannelEventType.WORKSPACE_CHANNEL_CREATE)
                .name("Acme").userId(7).members(List.of(7)).build());

        ArgumentCaptor<Channel> saved = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(saved.capture());
        Channel c = saved.getValue();
        assertThat(c.getType()).isEqualTo(ChannelType.GROUP);
        assertThat(c.getWorkspaceId()).isEqualTo(42);
        assertThat(c.getCanonical()).isTrue();
        assertThat(c.getName()).isEqualTo("Acme");
        assertThat(c.getMembers()).containsExactly(7);
        assertThat(c.getCreatedBy()).isEqualTo(7);
    }

    @Test
    void createIsIdempotentWhenChannelExists() {
        when(channelRepository.findCanonicalByWorkspaceId(42)).thenReturn(Optional.of(new Channel()));

        listener.handle(event(WorkspaceChannelEventType.WORKSPACE_CHANNEL_CREATE)
                .name("Acme").userId(7).members(List.of(7)).build());

        verify(channelRepository, never()).save(any());
    }

    @Test
    void createDefaultsNameWhenMissing() {
        when(channelRepository.findCanonicalByWorkspaceId(42)).thenReturn(Optional.empty());

        listener.handle(event(WorkspaceChannelEventType.WORKSPACE_CHANNEL_CREATE).userId(7).build());

        ArgumentCaptor<Channel> saved = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("general");
        assertThat(saved.getValue().getMembers()).isEmpty();
    }

    @Test
    void addMemberAppendsToCanonicalChannel() {
        ObjectId id = new ObjectId();
        Channel existing = Channel.builder().id(id).workspaceId(42).canonical(true).build();
        when(channelRepository.findCanonicalByWorkspaceId(42)).thenReturn(Optional.of(existing));

        listener.handle(event(WorkspaceChannelEventType.WORKSPACE_CHANNEL_ADD_MEMBER).userId(99).build());

        verify(channelRepository).addMember(eq(id), eq(99), any());
    }

    @Test
    void removeMemberPullsFromCanonicalChannel() {
        ObjectId id = new ObjectId();
        Channel existing = Channel.builder().id(id).workspaceId(42).canonical(true).build();
        when(channelRepository.findCanonicalByWorkspaceId(42)).thenReturn(Optional.of(existing));

        listener.handle(event(WorkspaceChannelEventType.WORKSPACE_CHANNEL_REMOVE_MEMBER).userId(99).build());

        verify(channelRepository).removeMember(eq(id), eq(99), any());
    }

    @Test
    void membershipEventWithoutCanonicalChannelIsDropped() {
        when(channelRepository.findCanonicalByWorkspaceId(42)).thenReturn(Optional.empty());

        listener.handle(event(WorkspaceChannelEventType.WORKSPACE_CHANNEL_ADD_MEMBER).userId(99).build());

        verify(channelRepository, never()).addMember(any(), any(), any());
    }

    @Test
    void malformedEventIsDropped() {
        listener.handle(WorkspaceChannelEvent.builder()
                .type(WorkspaceChannelEventType.WORKSPACE_CHANNEL_CREATE).build()); // no workspaceId

        verify(channelRepository, never()).save(any());
    }
}
