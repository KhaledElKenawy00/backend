package com.virtualoffice.chat_service.service.impl;

import com.virtualoffice.chat_service.dto.request.SendMessageRequest;
import com.virtualoffice.chat_service.dto.response.MessageResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.model.Message;
import com.virtualoffice.chat_service.model.MessageType;
import com.virtualoffice.chat_service.repository.MessageRepository;
import com.virtualoffice.chat_service.service.ChannelService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChannelService channelService;

    @InjectMocks
    private MessageServiceImpl messageService;

    private ObjectId channelId;
    private ObjectId messageId;
    private Message existingMessage;

    @BeforeEach
    void setUp() {
        channelId = new ObjectId();
        messageId = new ObjectId();

        existingMessage = Message.builder()
                .id(messageId)
                .channelId(channelId)
                .senderId(10)
                .senderRole("USER")
                .content("hello world")
                .type(MessageType.TEXT)
                .deleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ────────────────────────────────────────
    // sendMessage
    // ────────────────────────────────────────

    @Nested
    class SendMessage {

        @Test
        void shouldSendMessageSuccessfully() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(new ObjectId());
                return m;
            });

            SendMessageRequest request = SendMessageRequest.builder()
                    .content("hello")
                    .build();

            MessageResponse response = messageService.sendMessage(channelId.toHexString(), request, 10, "USER");

            assertThat(response.getContent()).isEqualTo("hello");
            assertThat(response.getSenderId()).isEqualTo(10);
            assertThat(response.getType()).isEqualTo("TEXT");
            assertThat(response.getDeleted()).isFalse();
        }

        @Test
        void shouldStoreSenderRole() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(new ObjectId());
                return m;
            });

            SendMessageRequest request = SendMessageRequest.builder().content("admin msg").build();
            messageService.sendMessage(channelId.toHexString(), request, 10, "ADMIN");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(captor.capture());
            assertThat(captor.getValue().getSenderRole()).isEqualTo("ADMIN");
        }

        @Test
        void shouldDefaultSenderRoleToUser() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(new ObjectId());
                return m;
            });

            SendMessageRequest request = SendMessageRequest.builder().content("test").build();
            messageService.sendMessage(channelId.toHexString(), request, 10, null);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(captor.capture());
            assertThat(captor.getValue().getSenderRole()).isEqualTo("USER");
        }

        @Test
        void shouldRejectNonMember() {
            when(channelService.isMember(channelId.toHexString(), 99)).thenReturn(false);

            SendMessageRequest request = SendMessageRequest.builder().content("test").build();

            assertThatThrownBy(() -> messageService.sendMessage(channelId.toHexString(), request, 99, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        void shouldSupportThreadIdAndReplyToId() {
            ObjectId threadId = new ObjectId();
            ObjectId replyToId = new ObjectId();

            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(new ObjectId());
                return m;
            });

            SendMessageRequest request = SendMessageRequest.builder()
                    .content("reply")
                    .threadId(threadId.toHexString())
                    .replyToId(replyToId.toHexString())
                    .build();

            MessageResponse response = messageService.sendMessage(channelId.toHexString(), request, 10, "USER");

            assertThat(response.getThreadId()).isEqualTo(threadId.toHexString());
            assertThat(response.getReplyToId()).isEqualTo(replyToId.toHexString());
        }

        @Test
        void shouldSupportMentions() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(new ObjectId());
                return m;
            });

            SendMessageRequest request = SendMessageRequest.builder()
                    .content("hey @user")
                    .mentions(List.of(20, 30))
                    .build();

            MessageResponse response = messageService.sendMessage(channelId.toHexString(), request, 10, "USER");
            assertThat(response.getMentions()).containsExactly(20, 30);
        }

        @Test
        void shouldReturnExistingMessageForDuplicateClientMessageId() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.findBySenderIdAndClientMessageId(10, "uuid-123"))
                    .thenReturn(Optional.of(existingMessage));

            SendMessageRequest request = SendMessageRequest.builder()
                    .content("retry")
                    .clientMessageId("uuid-123")
                    .build();

            MessageResponse response = messageService.sendMessage(channelId.toHexString(), request, 10, "USER");

            assertThat(response.getId()).isEqualTo(messageId.toHexString());
            verify(messageRepository, never()).save(any());
        }

        @Test
        void shouldSaveNewMessageWhenClientMessageIdNotFound() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.findBySenderIdAndClientMessageId(10, "uuid-new"))
                    .thenReturn(Optional.empty());
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(new ObjectId());
                return m;
            });

            SendMessageRequest request = SendMessageRequest.builder()
                    .content("new message")
                    .clientMessageId("uuid-new")
                    .build();

            messageService.sendMessage(channelId.toHexString(), request, 10, "USER");

            verify(messageRepository).save(any(Message.class));
        }

        @Test
        void shouldNormalizeBlankClientMessageIdToNull() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(new ObjectId());
                return m;
            });

            SendMessageRequest request = SendMessageRequest.builder()
                    .content("test")
                    .clientMessageId("   ")  // blank, should become null
                    .build();

            messageService.sendMessage(channelId.toHexString(), request, 10, "USER");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(captor.capture());
            assertThat(captor.getValue().getClientMessageId()).isNull();
        }

        @Test
        void shouldNotPerformDedupLookupWhenClientMessageIdIsBlank() {
            // C1 regression — normalize must run BEFORE the dedup lookup so two
            // empty-string retries don't both hit the index with ""
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(new ObjectId());
                return m;
            });

            SendMessageRequest request = SendMessageRequest.builder()
                    .content("test")
                    .clientMessageId("")  // empty after blank-normalize
                    .build();

            messageService.sendMessage(channelId.toHexString(), request, 10, "USER");

            verify(messageRepository, never()).findBySenderIdAndClientMessageId(anyInt(), any());
            verify(messageRepository).save(any(Message.class));
        }

        @Test
        void shouldNotPerformDedupLookupWhenClientMessageIdIsAbsent() {
            when(channelService.isMember(channelId.toHexString(), 10)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(new ObjectId());
                return m;
            });

            SendMessageRequest request = SendMessageRequest.builder().content("test").build();

            messageService.sendMessage(channelId.toHexString(), request, 10, "USER");

            verify(messageRepository, never()).findBySenderIdAndClientMessageId(anyInt(), any());
        }
    }

    // ────────────────────────────────────────
    // getChannelMessages
    // ────────────────────────────────────────

    @Nested
    class GetChannelMessages {

        @Test
        void shouldReturnPagedMessages() {
            Page<Message> page = new PageImpl<>(List.of(existingMessage));
            when(messageRepository.findChannelMessages(eq(channelId), any(Pageable.class))).thenReturn(page);

            PaginatedResponse<MessageResponse> result = messageService.getChannelMessages(channelId.toHexString(), 1, 50);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getCurrentPage()).isEqualTo(1);
        }

        @Test
        void shouldReturnMessagesBefore() {
            ObjectId cursorId = new ObjectId();
            when(messageRepository.findChannelMessagesBefore(eq(channelId), eq(cursorId), any(Pageable.class)))
                    .thenReturn(List.of(existingMessage));

            List<MessageResponse> result = messageService.getChannelMessagesBefore(
                    channelId.toHexString(), cursorId.toHexString(), 50);

            assertThat(result).hasSize(1);
        }

        @Test
        void shouldReturnMessagesAfter() {
            ObjectId cursorId = new ObjectId();
            when(messageRepository.findChannelMessagesAfter(eq(channelId), eq(cursorId), any(Pageable.class)))
                    .thenReturn(List.of(existingMessage));

            List<MessageResponse> result = messageService.getChannelMessagesAfter(
                    channelId.toHexString(), cursorId.toHexString(), 50);

            assertThat(result).hasSize(1);
        }
    }

    // ────────────────────────────────────────
    // getThreadMessages
    // ────────────────────────────────────────

    @Nested
    class GetThreadMessages {

        @Test
        void shouldReturnPagedThreadMessages() {
            ObjectId threadId = new ObjectId();
            Page<Message> page = new PageImpl<>(List.of(existingMessage));
            when(messageRepository.findThreadMessages(eq(threadId), any(Pageable.class))).thenReturn(page);

            PaginatedResponse<MessageResponse> result = messageService.getThreadMessages(threadId.toHexString(), 1, 50);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        void shouldReturnThreadMessagesBefore() {
            ObjectId threadId = new ObjectId();
            ObjectId cursorId = new ObjectId();
            when(messageRepository.findThreadMessagesBefore(eq(threadId), eq(cursorId), any(Pageable.class)))
                    .thenReturn(List.of(existingMessage));

            List<MessageResponse> result = messageService.getThreadMessagesBefore(
                    threadId.toHexString(), cursorId.toHexString(), 50);

            assertThat(result).hasSize(1);
        }

        @Test
        void shouldReturnThreadMessagesAfter() {
            ObjectId threadId = new ObjectId();
            ObjectId cursorId = new ObjectId();
            when(messageRepository.findThreadMessagesAfter(eq(threadId), eq(cursorId), any(Pageable.class)))
                    .thenReturn(List.of(existingMessage));

            List<MessageResponse> result = messageService.getThreadMessagesAfter(
                    threadId.toHexString(), cursorId.toHexString(), 50);

            assertThat(result).hasSize(1);
        }
    }

    // ────────────────────────────────────────
    // editMessage
    // ────────────────────────────────────────

    @Nested
    class EditMessage {

        @Test
        void shouldEditOwnMessage() {
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));
            when(messageRepository.save(any(Message.class))).thenReturn(existingMessage);

            MessageResponse response = messageService.editMessage(messageId.toHexString(), "updated", 10, "USER");

            assertThat(response.getContent()).isEqualTo("updated");
            verify(messageRepository).save(existingMessage);
        }

        @Test
        void shouldRejectEditByNonSender() {
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));

            assertThatThrownBy(() -> messageService.editMessage(messageId.toHexString(), "hack", 99, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("you can only edit your own messages");
        }

        @Test
        void shouldRejectEditByAdmin() {
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));

            assertThatThrownBy(() -> messageService.editMessage(messageId.toHexString(), "admin edit", 99, "ADMIN"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("you can only edit your own messages");
        }

        @Test
        void shouldRejectEditOfDeletedMessage() {
            existingMessage.setDeleted(true);
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));

            assertThatThrownBy(() -> messageService.editMessage(messageId.toHexString(), "revive", 10, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("cannot edit a deleted message");
        }

        @Test
        void shouldThrow404ForMissingMessage() {
            when(messageRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.editMessage(new ObjectId().toHexString(), "x", 10, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("message not found");
        }

        @Test
        void shouldUpdateTimestamp() {
            Instant before = Instant.now();
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));
            when(messageRepository.save(any(Message.class))).thenReturn(existingMessage);

            messageService.editMessage(messageId.toHexString(), "new text", 10, "USER");

            assertThat(existingMessage.getUpdatedAt()).isAfterOrEqualTo(before);
        }
    }

    // ────────────────────────────────────────
    // deleteMessage
    // ────────────────────────────────────────

    @Nested
    class DeleteMessage {

        @Test
        void shouldSoftDeleteOwnMessage() {
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));
            when(messageRepository.save(any(Message.class))).thenReturn(existingMessage);

            MessageResponse response = messageService.deleteMessage(messageId.toHexString(), 10, "USER");

            assertThat(existingMessage.getDeleted()).isTrue();
            assertThat(existingMessage.getContent()).isNull();
            assertThat(existingMessage.getDeletedAt()).isNotNull();
            assertThat(response).isNotNull();
            verify(messageRepository).save(existingMessage);
        }

        @Test
        void shouldAllowAdminToDeleteNormalUserMessage() {
            existingMessage.setSenderRole("USER");
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));
            when(messageRepository.save(any(Message.class))).thenReturn(existingMessage);

            MessageResponse response = messageService.deleteMessage(messageId.toHexString(), 99, "ADMIN");

            assertThat(existingMessage.getDeleted()).isTrue();
            assertThat(response).isNotNull();
            verify(messageRepository).save(existingMessage);
        }

        @Test
        void shouldRejectAdminDeletingAdminMessage() {
            existingMessage.setSenderRole("ADMIN");
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));

            assertThatThrownBy(() -> messageService.deleteMessage(messageId.toHexString(), 99, "ADMIN"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("admins cannot delete other admins' messages");
        }

        @Test
        void shouldRejectNonOwnerNonAdmin() {
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));

            assertThatThrownBy(() -> messageService.deleteMessage(messageId.toHexString(), 99, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("you can only delete your own messages");
        }

        @Test
        void shouldReturnNullIfAlreadyDeleted() {
            existingMessage.setDeleted(true);
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));

            MessageResponse response = messageService.deleteMessage(messageId.toHexString(), 10, "USER");

            assertThat(response).isNull();
            verify(messageRepository, never()).save(any());
        }

        @Test
        void shouldThrow404ForMissingMessage() {
            when(messageRepository.findById(any(ObjectId.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.deleteMessage(new ObjectId().toHexString(), 10, "USER"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("message not found");
        }

        @Test
        void shouldSetDeletedAtTimestamp() {
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));
            when(messageRepository.save(any(Message.class))).thenReturn(existingMessage);

            messageService.deleteMessage(messageId.toHexString(), 10, "USER");

            assertThat(existingMessage.getDeletedAt()).isNotNull();
        }

        @Test
        void shouldAllowAdminToDeleteOwnMessage() {
            // admin deleting their own message — always allowed
            existingMessage.setSenderId(99);
            existingMessage.setSenderRole("ADMIN");
            when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));
            when(messageRepository.save(any(Message.class))).thenReturn(existingMessage);

            MessageResponse response = messageService.deleteMessage(messageId.toHexString(), 99, "ADMIN");

            assertThat(existingMessage.getDeleted()).isTrue();
            assertThat(response).isNotNull();
        }
    }
}
