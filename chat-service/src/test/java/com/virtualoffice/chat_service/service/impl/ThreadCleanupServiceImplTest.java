package com.virtualoffice.chat_service.service.impl;

import com.virtualoffice.chat_service.model.Message;
import com.virtualoffice.chat_service.model.MessageType;
import com.virtualoffice.chat_service.repository.MessageRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreadCleanupServiceImplTest {

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ThreadCleanupServiceImpl threadCleanupService;

    @Test
    void shouldSoftDeleteAllThreadMessages() {
        ObjectId threadId = new ObjectId();
        Message msg1 = Message.builder()
                .id(new ObjectId()).channelId(new ObjectId()).senderId(10)
                .content("msg1").type(MessageType.TEXT).threadId(threadId)
                .deleted(false).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        Message msg2 = Message.builder()
                .id(new ObjectId()).channelId(new ObjectId()).senderId(20)
                .content("msg2").type(MessageType.TEXT).threadId(threadId)
                .deleted(false).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        when(messageRepository.findAllByThreadId(threadId)).thenReturn(List.of(msg1, msg2));

        threadCleanupService.cleanupThreadMessages(threadId);

        assertThat(msg1.getDeleted()).isTrue();
        assertThat(msg1.getContent()).isNull();
        assertThat(msg1.getDeletedAt()).isNotNull();
        assertThat(msg2.getDeleted()).isTrue();
        assertThat(msg2.getContent()).isNull();

        verify(messageRepository).saveAll(List.of(msg1, msg2));
    }

    @Test
    void shouldSkipAlreadyDeletedMessages() {
        ObjectId threadId = new ObjectId();
        Message alreadyDeleted = Message.builder()
                .id(new ObjectId()).channelId(new ObjectId()).senderId(10)
                .content(null).type(MessageType.TEXT).threadId(threadId)
                .deleted(true).deletedAt(Instant.now())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        Instant originalDeletedAt = alreadyDeleted.getDeletedAt();

        when(messageRepository.findAllByThreadId(threadId)).thenReturn(List.of(alreadyDeleted));

        threadCleanupService.cleanupThreadMessages(threadId);

        // should not overwrite the existing deletedAt
        assertThat(alreadyDeleted.getDeletedAt()).isEqualTo(originalDeletedAt);
        verify(messageRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldHandleEmptyThreadGracefully() {
        ObjectId threadId = new ObjectId();
        when(messageRepository.findAllByThreadId(threadId)).thenReturn(List.of());

        threadCleanupService.cleanupThreadMessages(threadId);

        verify(messageRepository, never()).saveAll(any());
    }

    @Test
    void shouldNotThrowOnRepositoryError() {
        ObjectId threadId = new ObjectId();
        when(messageRepository.findAllByThreadId(threadId)).thenThrow(new RuntimeException("db down"));

        // should not throw — method catches exceptions internally
        threadCleanupService.cleanupThreadMessages(threadId);
    }
}
