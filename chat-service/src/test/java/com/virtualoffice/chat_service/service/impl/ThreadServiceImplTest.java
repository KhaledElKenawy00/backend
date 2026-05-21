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

import com.virtualoffice.chat_service.dto.request.CreateThreadRequest;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.dto.response.ThreadResponse;
import com.virtualoffice.chat_service.model.ChatThread;
import com.virtualoffice.chat_service.model.Message;
import com.virtualoffice.chat_service.model.MessageType;
import com.virtualoffice.chat_service.repository.MessageRepository;
import com.virtualoffice.chat_service.repository.ThreadRepository;
import com.virtualoffice.chat_service.service.ChannelService;
import com.virtualoffice.chat_service.service.ThreadCleanupService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreadServiceImplTest {

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChannelService channelService;

    @Mock
    private ThreadCleanupService threadCleanupService;

    @InjectMocks
    private ThreadServiceImpl threadService;

    private ObjectId channelId;
    private ObjectId rootMessageId;
    private ObjectId threadId;
    private Message rootMessage;
    private ChatThread existingThread;

    @BeforeEach
    void setUp() {
        channelId = new ObjectId();
        rootMessageId = new ObjectId();
        threadId = new ObjectId();

        rootMessage = Message.builder()
                .id(rootMessageId)
                .channelId(channelId)
                .senderId(10)
                .content("root message")
                .type(MessageType.TEXT)
                .threadId(null) // top-level
                .deleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        existingThread = ChatThread.builder()
                .id(threadId)
                .channelId(channelId)
                .rootMessageId(rootMessageId)
                .name("discussion")
                .createdBy(10)
                .creatorRole("USER")
                .deleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ────────────────────────────────────────
    // createThread
    // ────────────────────────────────────────

    @Nested
    class CreateThread {

        @Test
        void shouldCreateThreadSuccessfully() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.findById(rootMessageId)).thenReturn(Optional.of(rootMessage));
            when(threadRepository.existsByRootMessageId(rootMessageId)).thenReturn(false);
            when(threadRepository.save(any(ChatThread.class))).thenAnswer(inv -> {
                ChatThread t = inv.getArgument(0);
                t.setId(new ObjectId());
                return t;
            });

            CreateThreadRequest request = CreateThreadRequest.builder()
                    .rootMessageId(rootMessageId.toHexString())
                    .name("discussion")
                    .build();

            ThreadResponse response = threadService.createThread(channelId.toHexString(), request, 10, "USER");

            assertThat(response.getName()).isEqualTo("discussion");
            assertThat(response.getChannelId()).isEqualTo(channelId.toHexString());
        }

        @Test
        void shouldStoreCreatorRole() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.findById(rootMessageId)).thenReturn(Optional.of(rootMessage));
            when(threadRepository.existsByRootMessageId(rootMessageId)).thenReturn(false);
            when(threadRepository.save(any(ChatThread.class))).thenAnswer(inv -> {
                ChatThread t = inv.getArgument(0);
                t.setId(new ObjectId());
                return t;
            });

            CreateThreadRequest request = CreateThreadRequest.builder()
                    .rootMessageId(rootMessageId.toHexString())
                    .name("admin thread")
                    .build();

            threadService.createThread(channelId.toHexString(), request, 10, "ADMIN");

            var captor = org.mockito.ArgumentCaptor.forClass(ChatThread.class);
            verify(threadRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatorRole()).isEqualTo("ADMIN");
        }

        @Test
        void shouldRejectNonMember() {
            when(channelService.isMember(channelId.toHexString(), 99)).thenReturn(false);

            CreateThreadRequest request = CreateThreadRequest.builder()
                    .rootMessageId(rootMessageId.toHexString())
                    .name("test")
                    .build();

            assertThatThrownBy(() -> threadService.createThread(channelId.toHexString(), request, 99, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        void shouldRejectWhenRootMessageNotFound() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());

            CreateThreadRequest request = CreateThreadRequest.builder()
                    .rootMessageId(new ObjectId().toHexString())
                    .name("test")
                    .build();

            assertThatThrownBy(() -> threadService.createThread(channelId.toHexString(), request, 10, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("root message not found");
        }

        @Test
        void shouldRejectWhenRootMessageBelongsToDifferentChannel() {
            Message differentChannelMsg = Message.builder()
                    .id(rootMessageId)
                    .channelId(new ObjectId()) // different channel!
                    .senderId(10).content("x").type(MessageType.TEXT).deleted(false)
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();

            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.findById(rootMessageId)).thenReturn(Optional.of(differentChannelMsg));

            CreateThreadRequest request = CreateThreadRequest.builder()
                    .rootMessageId(rootMessageId.toHexString())
                    .name("test")
                    .build();

            assertThatThrownBy(() -> threadService.createThread(channelId.toHexString(), request, 10, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("message does not belong to this channel");
        }

        @Test
        void shouldRejectWhenRootMessageIsInsideAThread() {
            rootMessage.setThreadId(new ObjectId());
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.findById(rootMessageId)).thenReturn(Optional.of(rootMessage));

            CreateThreadRequest request = CreateThreadRequest.builder()
                    .rootMessageId(rootMessageId.toHexString())
                    .name("test")
                    .build();

            assertThatThrownBy(() -> threadService.createThread(channelId.toHexString(), request, 10, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("already inside a thread");
        }

        @Test
        void shouldReturn409WhenThreadAlreadyExistsForRootMessage() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.findById(rootMessageId)).thenReturn(Optional.of(rootMessage));
            when(threadRepository.existsByRootMessageId(rootMessageId)).thenReturn(true);

            CreateThreadRequest request = CreateThreadRequest.builder()
                    .rootMessageId(rootMessageId.toHexString())
                    .name("duplicate")
                    .build();

            assertThatThrownBy(() -> threadService.createThread(channelId.toHexString(), request, 10, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("a thread already exists");
        }
    }

    // ────────────────────────────────────────
    // getChannelThreads
    // ────────────────────────────────────────

    @Nested
    class GetChannelThreads {

        @Test
        void shouldReturnPaginatedThreads() {
            Page<ChatThread> page = new PageImpl<>(List.of(existingThread));
            when(threadRepository.findActiveThreadsByChannelId(eq(channelId), any(Pageable.class))).thenReturn(page);

            PaginatedResponse<ThreadResponse> result = threadService.getChannelThreads(channelId.toHexString(), 1, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("discussion");
        }

        @Test
        void shouldReturnEmptyPageWhenNoThreads() {
            Page<ChatThread> emptyPage = new PageImpl<>(List.of());
            when(threadRepository.findActiveThreadsByChannelId(eq(channelId), any(Pageable.class))).thenReturn(emptyPage);

            PaginatedResponse<ThreadResponse> result = threadService.getChannelThreads(channelId.toHexString(), 1, 20);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ────────────────────────────────────────
    // getThread
    // ────────────────────────────────────────

    @Nested
    class GetThread {

        @Test
        void shouldReturnThread() {
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(existingThread));

            ThreadResponse response = threadService.getThread(threadId.toHexString());

            assertThat(response.getId()).isEqualTo(threadId.toHexString());
            assertThat(response.getName()).isEqualTo("discussion");
        }

        @Test
        void shouldThrow404WhenNotFound() {
            when(threadRepository.findActiveById(any(ObjectId.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> threadService.getThread(new ObjectId().toHexString()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("thread not found");
        }
    }

    // ────────────────────────────────────────
    // deleteThread
    // ────────────────────────────────────────

    @Nested
    class DeleteThread {

        @Test
        void shouldSoftDeleteByCreator() {
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(existingThread));

            threadService.deleteThread(threadId.toHexString(), 10, "USER");

            assertThat(existingThread.getDeleted()).isTrue();
            verify(threadRepository).save(existingThread);
            verify(threadCleanupService).cleanupThreadMessages(threadId);
        }

        @Test
        void shouldAllowAdminToDeleteNormalUserThread() {
            existingThread.setCreatorRole("USER");
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(existingThread));

            threadService.deleteThread(threadId.toHexString(), 99, "ADMIN");

            assertThat(existingThread.getDeleted()).isTrue();
            verify(threadCleanupService).cleanupThreadMessages(threadId);
        }

        @Test
        void shouldRejectAdminDeletingAdminThread() {
            existingThread.setCreatorRole("ADMIN");
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(existingThread));

            assertThatThrownBy(() -> threadService.deleteThread(threadId.toHexString(), 99, "ADMIN"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("admins cannot delete other admins' threads");
        }

        @Test
        void shouldRejectNonCreatorNonAdmin() {
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(existingThread));

            assertThatThrownBy(() -> threadService.deleteThread(threadId.toHexString(), 99, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("you can only delete your own threads");
        }

        @Test
        void shouldNoOpIfAlreadyDeleted() {
            // findActiveById uses { deleted: false }, so a deleted thread isn't found
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> threadService.deleteThread(threadId.toHexString(), 10, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("thread not found");

            verify(threadRepository, never()).save(any());
            verify(threadCleanupService, never()).cleanupThreadMessages(any());
        }

        @Test
        void shouldThrow404WhenNotFound() {
            when(threadRepository.findActiveById(any(ObjectId.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> threadService.deleteThread(new ObjectId().toHexString(), 10, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("thread not found");
        }

        @Test
        void shouldTriggerAsyncCleanup() {
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(existingThread));

            threadService.deleteThread(threadId.toHexString(), 10, "USER");

            verify(threadCleanupService).cleanupThreadMessages(threadId);
        }

        @Test
        void shouldAllowAdminToDeleteOwnThread() {
            existingThread.setCreatedBy(99);
            existingThread.setCreatorRole("ADMIN");
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(existingThread));

            threadService.deleteThread(threadId.toHexString(), 99, "ADMIN");

            assertThat(existingThread.getDeleted()).isTrue();
        }
    }
}
