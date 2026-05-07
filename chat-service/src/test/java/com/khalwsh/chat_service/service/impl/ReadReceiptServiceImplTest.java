package com.khalwsh.chat_service.service.impl;

import com.khalwsh.chat_service.dto.response.UnreadCountResponse;
import com.khalwsh.chat_service.model.Message;
import com.khalwsh.chat_service.model.MessageType;
import com.khalwsh.chat_service.repository.MessageRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
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
class ReadReceiptServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ReadReceiptServiceImpl readReceiptService;

    // helpers ────────────────────────────────────────

    private Message channelMsg(ObjectId id, ObjectId channelId) {
        return Message.builder()
                .id(id)
                .channelId(channelId)
                .threadId(null)
                .senderId(10)
                .type(MessageType.TEXT)
                .deleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Message threadMsg(ObjectId id, ObjectId channelId, ObjectId threadId) {
        return Message.builder()
                .id(id)
                .channelId(channelId)
                .threadId(threadId)
                .senderId(10)
                .type(MessageType.TEXT)
                .deleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ────────────────────────────────────────
    // channel mark-as-read — validation
    // ────────────────────────────────────────

    @Nested
    class ChannelMarkAsRead {

        @Test
        void shouldCallLuaScriptWithCorrectKey() {
            ObjectId channelId = new ObjectId();
            ObjectId messageId = new ObjectId();
            when(messageRepository.findById(messageId))
                    .thenReturn(Optional.of(channelMsg(messageId, channelId)));

            readReceiptService.markAsRead(channelId.toHexString(), 10, messageId.toHexString());

            verify(redisTemplate).execute(
                    any(RedisScript.class),
                    eq(List.of("read:" + channelId.toHexString() + ":10")),
                    eq(messageId.toHexString()),
                    eq(String.valueOf(30L * 24 * 3600))
            );
        }

        @Test
        void shouldRejectWhenMessageNotFound() {
            ObjectId channelId = new ObjectId();
            ObjectId messageId = new ObjectId();
            when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    readReceiptService.markAsRead(channelId.toHexString(), 10, messageId.toHexString()))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> {
                        ResponseStatusException rse = (ResponseStatusException) e;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).contains("not found");
                    });
            verifyNoInteractions(redisTemplate);
        }

        @Test
        void shouldRejectWhenMessageBelongsToAnotherChannel() {
            ObjectId myChannel = new ObjectId();
            ObjectId otherChannel = new ObjectId();
            ObjectId messageId = new ObjectId();
            when(messageRepository.findById(messageId))
                    .thenReturn(Optional.of(channelMsg(messageId, otherChannel)));

            assertThatThrownBy(() ->
                    readReceiptService.markAsRead(myChannel.toHexString(), 10, messageId.toHexString()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("does not belong to this channel");
            verifyNoInteractions(redisTemplate);
        }

        @Test
        void shouldRejectWhenMessageIsAThreadReply() {
            ObjectId channelId = new ObjectId();
            ObjectId threadId = new ObjectId();
            ObjectId messageId = new ObjectId();
            when(messageRepository.findById(messageId))
                    .thenReturn(Optional.of(threadMsg(messageId, channelId, threadId)));

            assertThatThrownBy(() ->
                    readReceiptService.markAsRead(channelId.toHexString(), 10, messageId.toHexString()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("thread reply");
            verifyNoInteractions(redisTemplate);
        }

        @Test
        void shouldRejectWhenMessageIdIsMalformed() {
            ObjectId channelId = new ObjectId();

            assertThatThrownBy(() ->
                    readReceiptService.markAsRead(channelId.toHexString(), 10, "not-an-objectid"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
            verifyNoInteractions(redisTemplate);
        }
    }

    @Nested
    class ChannelUnreadCount {

        @Test
        void shouldReturnUnreadCountWithLastReadPosition() {
            String channelId = new ObjectId().toHexString();
            ObjectId lastRead = new ObjectId();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("read:" + channelId + ":10")).thenReturn(lastRead.toHexString());
            when(messageRepository.countChannelMessagesAfter(eq(new ObjectId(channelId)), eq(lastRead)))
                    .thenReturn(5L);

            UnreadCountResponse response = readReceiptService.getUnreadCount(channelId, 10);

            assertThat(response.getUnreadCount()).isEqualTo(5);
            assertThat(response.getLastReadMessageId()).isEqualTo(lastRead.toHexString());
            verify(messageRepository, never()).countChannelMessages(any());
        }

        @Test
        void shouldUseTotalCountWhenNeverRead() {
            String channelId = new ObjectId().toHexString();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("read:" + channelId + ":10")).thenReturn(null);
            when(messageRepository.countChannelMessages(new ObjectId(channelId))).thenReturn(42L);

            UnreadCountResponse response = readReceiptService.getUnreadCount(channelId, 10);

            assertThat(response.getUnreadCount()).isEqualTo(42);
            assertThat(response.getLastReadMessageId()).isNull();
            verify(messageRepository, never()).countChannelMessagesAfter(any(), any());
        }
    }

    // ────────────────────────────────────────
    // thread mark-as-read — validation
    // ────────────────────────────────────────

    @Nested
    class ThreadMarkAsRead {

        @Test
        void shouldCallLuaScriptForThread() {
            ObjectId channelId = new ObjectId();
            ObjectId threadId = new ObjectId();
            ObjectId messageId = new ObjectId();
            when(messageRepository.findById(messageId))
                    .thenReturn(Optional.of(threadMsg(messageId, channelId, threadId)));

            readReceiptService.markThreadAsRead(threadId.toHexString(), 10, messageId.toHexString());

            verify(redisTemplate).execute(
                    any(RedisScript.class),
                    eq(List.of("read:thread:" + threadId.toHexString() + ":10")),
                    eq(messageId.toHexString()),
                    eq(String.valueOf(30L * 24 * 3600))
            );
        }

        @Test
        void shouldRejectChannelMessageMarkedAsThreadRead() {
            ObjectId channelId = new ObjectId();
            ObjectId threadId = new ObjectId();
            ObjectId messageId = new ObjectId();
            when(messageRepository.findById(messageId))
                    .thenReturn(Optional.of(channelMsg(messageId, channelId)));

            assertThatThrownBy(() ->
                    readReceiptService.markThreadAsRead(threadId.toHexString(), 10, messageId.toHexString()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("does not belong to this thread");
            verifyNoInteractions(redisTemplate);
        }

        @Test
        void shouldRejectWhenMessageBelongsToAnotherThread() {
            ObjectId channelId = new ObjectId();
            ObjectId myThread = new ObjectId();
            ObjectId otherThread = new ObjectId();
            ObjectId messageId = new ObjectId();
            when(messageRepository.findById(messageId))
                    .thenReturn(Optional.of(threadMsg(messageId, channelId, otherThread)));

            assertThatThrownBy(() ->
                    readReceiptService.markThreadAsRead(myThread.toHexString(), 10, messageId.toHexString()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("does not belong to this thread");
            verifyNoInteractions(redisTemplate);
        }

        @Test
        void shouldRejectWhenMessageNotFound() {
            ObjectId threadId = new ObjectId();
            ObjectId messageId = new ObjectId();
            when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    readReceiptService.markThreadAsRead(threadId.toHexString(), 10, messageId.toHexString()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not found");
            verifyNoInteractions(redisTemplate);
        }
    }

    @Nested
    class ThreadUnreadCount {

        @Test
        void shouldReturnThreadUnreadCount() {
            String threadId = new ObjectId().toHexString();
            ObjectId lastRead = new ObjectId();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("read:thread:" + threadId + ":10")).thenReturn(lastRead.toHexString());
            when(messageRepository.countThreadMessagesAfter(eq(new ObjectId(threadId)), eq(lastRead)))
                    .thenReturn(3L);

            UnreadCountResponse response = readReceiptService.getThreadUnreadCount(threadId, 10);

            assertThat(response.getUnreadCount()).isEqualTo(3);
            assertThat(response.getLastReadMessageId()).isEqualTo(lastRead.toHexString());
            verify(messageRepository, never()).countThreadMessages(any());
        }

        @Test
        void shouldUseTotalCountWhenNeverRead() {
            String threadId = new ObjectId().toHexString();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("read:thread:" + threadId + ":10")).thenReturn(null);
            when(messageRepository.countThreadMessages(new ObjectId(threadId))).thenReturn(15L);

            UnreadCountResponse response = readReceiptService.getThreadUnreadCount(threadId, 10);

            assertThat(response.getUnreadCount()).isEqualTo(15);
            assertThat(response.getLastReadMessageId()).isNull();
            verify(messageRepository, never()).countThreadMessagesAfter(any(), any());
        }
    }
}
