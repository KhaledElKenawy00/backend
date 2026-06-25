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
package com.virtualoffice.chat_service.service.impl;

import com.virtualoffice.chat_service.client.WorkspaceClient;
import com.virtualoffice.chat_service.client.WorkspaceRole;
import com.virtualoffice.chat_service.dto.request.CreateChannelRequest;
import com.virtualoffice.chat_service.dto.response.ChannelResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChannelType;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelServiceImplTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private WorkspaceClient workspaceClient;

    @InjectMocks
    private ChannelServiceImpl channelService;

    private ObjectId channelId;
    private Channel groupChannel;
    private Channel dmChannel;

    @BeforeEach
    void setUp() {
        channelId = new ObjectId();
        groupChannel = Channel.builder()
                .id(channelId)
                .name("general")
                .type(ChannelType.GROUP)
                .workspaceId(1)
                .members(new ArrayList<>(List.of(10, 20)))
                .createdBy(10)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        dmChannel = Channel.builder()
                .id(new ObjectId())
                .type(ChannelType.DIRECT)
                .members(new ArrayList<>(List.of(10, 20)))
                .dmKey("10_20")
                .createdBy(10)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ────────────────────────────────────────
    // createGroupChannel
    // ────────────────────────────────────────

    @Nested
    class CreateGroupChannel {

        @Test
        void shouldCreateChannelSuccessfully() {
            CreateChannelRequest request = CreateChannelRequest.builder()
                    .name("general")
                    .workspaceId(1)
                    .members(List.of(10, 20))
                    .build();

            when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
                Channel ch = inv.getArgument(0);
                ch.setId(new ObjectId());
                return ch;
            });

            ChannelResponse response = channelService.createGroupChannel(request, 10);

            assertThat(response.getName()).isEqualTo("general");
            assertThat(response.getType()).isEqualTo("GROUP");
            assertThat(response.getWorkspaceId()).isEqualTo(1);

            ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
            verify(channelRepository).save(captor.capture());
            assertThat(captor.getValue().getMembers()).contains(10, 20);
        }

        @Test
        void shouldAutoAddCreatorToMembers() {
            CreateChannelRequest request = CreateChannelRequest.builder()
                    .name("dev")
                    .workspaceId(1)
                    .members(List.of(20, 30))
                    .build();

            when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
                Channel ch = inv.getArgument(0);
                ch.setId(new ObjectId());
                return ch;
            });

            channelService.createGroupChannel(request, 10);

            ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
            verify(channelRepository).save(captor.capture());
            assertThat(captor.getValue().getMembers()).contains(10, 20, 30);
        }

        @Test
        void shouldNotDuplicateCreatorIfAlreadyInMembers() {
            CreateChannelRequest request = CreateChannelRequest.builder()
                    .name("dev")
                    .workspaceId(1)
                    .members(List.of(10, 20))
                    .build();

            when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
                Channel ch = inv.getArgument(0);
                ch.setId(new ObjectId());
                return ch;
            });

            channelService.createGroupChannel(request, 10);

            ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
            verify(channelRepository).save(captor.capture());
            // should appear exactly once
            assertThat(captor.getValue().getMembers().stream().filter(m -> m == 10).count()).isEqualTo(1);
        }

        @Test
        void shouldRejectNullWorkspaceId() {
            CreateChannelRequest request = CreateChannelRequest.builder()
                    .name("no-workspace")
                    .workspaceId(null)
                    .members(List.of(10))
                    .build();

            assertThatThrownBy(() -> channelService.createGroupChannel(request, 10))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("workspaceId is required");
        }

        @Test
        void shouldEnforceMemberRoleInWorkspace() {
            CreateChannelRequest request = CreateChannelRequest.builder()
                    .name("general").workspaceId(1).members(List.of(20)).build();
            when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
                Channel ch = inv.getArgument(0);
                ch.setId(new ObjectId());
                return ch;
            });

            channelService.createGroupChannel(request, 10);

            // the workspace-scoped role check runs before any persistence
            verify(workspaceClient).requireRole(1, 10, WorkspaceRole.MEMBER);
        }

        @Test
        void shouldRejectCreationWhenNotAuthorizedInWorkspace() {
            CreateChannelRequest request = CreateChannelRequest.builder()
                    .name("general").workspaceId(1).members(List.of(20)).build();
            doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member"))
                    .when(workspaceClient).requireRole(1, 99, WorkspaceRole.MEMBER);

            assertThatThrownBy(() -> channelService.createGroupChannel(request, 99))
                    .isInstanceOf(ResponseStatusException.class);

            verify(channelRepository, never()).save(any());
        }

        @Test
        void shouldThrowDuplicateKeyOnSameNameInWorkspace() {
            CreateChannelRequest request = CreateChannelRequest.builder()
                    .name("general")
                    .workspaceId(1)
                    .members(List.of(10))
                    .build();
            // the (workspaceId, name) unique index fires
            when(channelRepository.save(any(Channel.class)))
                    .thenThrow(new DuplicateKeyException("idx_workspace_name dup"));

            assertThatThrownBy(() -> channelService.createGroupChannel(request, 10))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void shouldDeduplicateAndDropNullMembers() {
            List<Integer> members = new ArrayList<>();
            members.add(20);
            members.add(20);
            members.add(null);
            CreateChannelRequest request = CreateChannelRequest.builder()
                    .name("dedup")
                    .workspaceId(1)
                    .members(members)
                    .build();
            when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
                Channel ch = inv.getArgument(0);
                ch.setId(new ObjectId());
                return ch;
            });

            channelService.createGroupChannel(request, 10);

            ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
            verify(channelRepository).save(captor.capture());
            List<Integer> saved = captor.getValue().getMembers();
            assertThat(saved).containsExactlyInAnyOrder(10, 20);
            assertThat(saved).doesNotContainNull();
        }
    }

    // ────────────────────────────────────────
    // getWorkspaceChannels
    // ────────────────────────────────────────

    @Nested
    class GetWorkspaceChannels {

        @Test
        void shouldReturnPaginatedChannels() {
            Page<Channel> page = new PageImpl<>(List.of(groupChannel));
            when(channelRepository.findWorkspaceChannelsForUser(eq(1), eq(10), any(Pageable.class)))
                    .thenReturn(page);

            PaginatedResponse<ChannelResponse> result = channelService.getWorkspaceChannels(1, 10, 1, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("general");
            assertThat(result.getCurrentPage()).isEqualTo(1);
        }

        @Test
        void shouldReturnEmptyWhenNoChannels() {
            Page<Channel> emptyPage = new PageImpl<>(List.of());
            when(channelRepository.findWorkspaceChannelsForUser(eq(1), eq(10), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PaginatedResponse<ChannelResponse> result = channelService.getWorkspaceChannels(1, 10, 1, 20);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ────────────────────────────────────────
    // getRoomChannels
    // ────────────────────────────────────────

    @Nested
    class GetRoomChannels {

        @Test
        void shouldReturnPaginatedRoomChannels() {
            Channel roomChannel = Channel.builder()
                    .id(new ObjectId())
                    .type(ChannelType.ROOM)
                    .workspaceId(1)
                    .members(new ArrayList<>(List.of(10)))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            Page<Channel> page = new PageImpl<>(List.of(roomChannel));
            when(channelRepository.findRoomChannelsForUser(eq(1), eq(10), any(Pageable.class)))
                    .thenReturn(page);

            PaginatedResponse<ChannelResponse> result = channelService.getRoomChannels(1, 10, 1, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getType()).isEqualTo("ROOM");
            assertThat(result.getCurrentPage()).isEqualTo(1);
        }

        @Test
        void shouldReturnEmptyWhenNoRoomChannels() {
            when(channelRepository.findRoomChannelsForUser(eq(1), eq(10), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            PaginatedResponse<ChannelResponse> result = channelService.getRoomChannels(1, 10, 1, 20);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ────────────────────────────────────────
    // getChannel
    // ────────────────────────────────────────

    @Nested
    class GetChannel {

        @Test
        void shouldReturnChannel() {
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(groupChannel));

            ChannelResponse response = channelService.getChannel(channelId.toHexString());

            assertThat(response.getId()).isEqualTo(channelId.toHexString());
            assertThat(response.getName()).isEqualTo("general");
        }

        @Test
        void shouldThrow404WhenNotFound() {
            when(channelRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelService.getChannel(new ObjectId().toHexString()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("channel not found");
        }
    }

    // ────────────────────────────────────────
    // joinChannel
    // ────────────────────────────────────────

    @Nested
    class JoinChannel {

        @Test
        void shouldAddUserToMembers() {
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(groupChannel));

            channelService.joinChannel(channelId.toHexString(), 30);

            verify(channelRepository).addMember(eq(channelId), eq(30), any(Instant.class));
            verify(channelRepository, never()).save(any(Channel.class));
        }

        @Test
        void shouldBeIdempotentIfAlreadyMember() {
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(groupChannel));

            channelService.joinChannel(channelId.toHexString(), 10);

            verify(channelRepository, never()).addMember(any(ObjectId.class), anyInt(), any(Instant.class));
            verify(channelRepository, never()).save(any());
        }

        @Test
        void shouldRejectJoiningDmChannel() {
            when(channelRepository.findById(dmChannel.getId())).thenReturn(Optional.of(dmChannel));

            assertThatThrownBy(() -> channelService.joinChannel(dmChannel.getId().toHexString(), 30))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("only group channels can be joined");
        }

        @Test
        void shouldThrow404WhenChannelNotFound() {
            when(channelRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelService.joinChannel(new ObjectId().toHexString(), 10))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("channel not found");
        }

        @Test
        void shouldNotPersistEntireDocumentOnJoin() {
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(groupChannel));

            channelService.joinChannel(channelId.toHexString(), 30);

            verify(channelRepository, never()).save(any(Channel.class));
        }
    }

    // ────────────────────────────────────────
    // leaveChannel
    // ────────────────────────────────────────

    @Nested
    class LeaveChannel {

        @Test
        void shouldRemoveUserFromMembers() {
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(groupChannel));

            channelService.leaveChannel(channelId.toHexString(), 20);

            verify(channelRepository).removeMember(eq(channelId), eq(20), any(Instant.class));
            verify(channelRepository).deleteIfEmpty(channelId);
            verify(channelRepository, never()).save(any(Channel.class));
        }

        @Test
        void shouldDeleteChannelWhenLastMemberLeaves() {
            // single-member group channel — leaving empties it
            Channel solo = Channel.builder()
                    .id(channelId)
                    .name("solo")
                    .type(ChannelType.GROUP)
                    .workspaceId(1)
                    .members(new ArrayList<>(List.of(10)))
                    .createdBy(10)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(solo));

            channelService.leaveChannel(channelId.toHexString(), 10);

            verify(channelRepository).removeMember(eq(channelId), eq(10), any(Instant.class));
            verify(channelRepository).deleteIfEmpty(channelId);
            verify(channelRepository, never()).save(any(Channel.class));
        }

        @Test
        void shouldRejectLeavingDmChannel() {
            when(channelRepository.findById(dmChannel.getId())).thenReturn(Optional.of(dmChannel));

            assertThatThrownBy(() -> channelService.leaveChannel(dmChannel.getId().toHexString(), 10))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("only group channels can be left");
        }

        @Test
        void shouldRejectIfNotAMember() {
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(groupChannel));

            assertThatThrownBy(() -> channelService.leaveChannel(channelId.toHexString(), 99))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        void shouldThrow404WhenChannelNotFound() {
            when(channelRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelService.leaveChannel(new ObjectId().toHexString(), 10))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("channel not found");
        }

        @Test
        void shouldNotPersistEntireDocumentOnLeave() {
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(groupChannel));

            channelService.leaveChannel(channelId.toHexString(), 20);

            verify(channelRepository, never()).save(any(Channel.class));
        }
    }

    // ────────────────────────────────────────
    // getOrCreateDm
    // ────────────────────────────────────────

    @Nested
    class GetOrCreateDm {

        @Test
        void shouldReturnExistingDm() {
            when(channelRepository.findByDmKey("10_20")).thenReturn(Optional.of(dmChannel));

            ChannelResponse response = channelService.getOrCreateDm(10, 20);

            assertThat(response.getType()).isEqualTo("DIRECT");
            verify(channelRepository, never()).save(any());
        }

        @Test
        void shouldCreateNewDmWhenNotExists() {
            when(channelRepository.findByDmKey("10_20")).thenReturn(Optional.empty());
            when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
                Channel ch = inv.getArgument(0);
                ch.setId(new ObjectId());
                return ch;
            });

            ChannelResponse response = channelService.getOrCreateDm(10, 20);

            assertThat(response.getType()).isEqualTo("DIRECT");
            ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
            verify(channelRepository).save(captor.capture());
            Channel saved = captor.getValue();
            assertThat(saved.getDmKey()).isEqualTo("10_20");
            assertThat(saved.getMembers()).containsExactlyInAnyOrder(10, 20);
            assertThat(saved.getWorkspaceId()).isNull();
        }

        @Test
        void shouldUseDeterministicDmKey() {
            // calling with (20, 10) should produce the same key "10_20"
            when(channelRepository.findByDmKey("10_20")).thenReturn(Optional.of(dmChannel));

            channelService.getOrCreateDm(20, 10);

            verify(channelRepository).findByDmKey("10_20");
        }

        @Test
        void shouldHandleRaceConditionGracefully() {
            when(channelRepository.findByDmKey("10_20"))
                    .thenReturn(Optional.empty())  // first lookup: nothing
                    .thenReturn(Optional.of(dmChannel)); // second lookup after race
            when(channelRepository.save(any(Channel.class)))
                    .thenThrow(new DuplicateKeyException("duplicate dmKey"));

            ChannelResponse response = channelService.getOrCreateDm(10, 20);

            assertThat(response).isNotNull();
            verify(channelRepository, times(2)).findByDmKey("10_20");
        }

        @Test
        void shouldRejectDmWithSelf() {
            assertThatThrownBy(() -> channelService.getOrCreateDm(10, 10))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("cannot create a DM with yourself");
        }
    }

    // ────────────────────────────────────────
    // getDirectMessages
    // ────────────────────────────────────────

    @Nested
    class GetDirectMessages {

        @Test
        void shouldReturnPaginatedDms() {
            Page<Channel> page = new PageImpl<>(List.of(dmChannel));
            when(channelRepository.findDirectChannelsForUser(eq(ChannelType.DIRECT), eq(10), any(Pageable.class)))
                    .thenReturn(page);

            PaginatedResponse<ChannelResponse> result = channelService.getDirectMessages(10, 1, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getType()).isEqualTo("DIRECT");
        }
    }

    // ────────────────────────────────────────
    // isMember
    // ────────────────────────────────────────

    @Nested
    class IsMember {

        @Test
        void shouldReturnTrueForMember() {
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(groupChannel));

            assertThat(channelService.isMember(channelId.toHexString(), 10)).isTrue();
        }

        @Test
        void shouldReturnFalseForNonMember() {
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(groupChannel));

            assertThat(channelService.isMember(channelId.toHexString(), 99)).isFalse();
        }

        @Test
        void shouldReturnFalseWhenChannelNotFound() {
            when(channelRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());

            assertThat(channelService.isMember(new ObjectId().toHexString(), 10)).isFalse();
        }
    }
}
