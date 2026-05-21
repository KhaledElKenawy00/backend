package com.virtualoffice.chat_service.dto.mapper;

import com.virtualoffice.chat_service.dto.response.ChannelResponse;
import com.virtualoffice.chat_service.dto.response.MessageResponse;
import com.virtualoffice.chat_service.dto.response.ThreadResponse;
import com.virtualoffice.chat_service.model.*;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtoMapperTest {

    // ────────────────────────────────────────
    // toChannelResponse
    // ────────────────────────────────────────

    @Nested
    class ToChannelResponse {

        @Test
        void shouldMapAllFields() {
            ObjectId id = new ObjectId();
            Instant now = Instant.now();
            Channel channel = Channel.builder()
                    .id(id)
                    .name("general")
                    .type(ChannelType.GROUP)
                    .workspaceId(5)
                    .members(List.of(1, 2, 3))
                    .createdBy(1)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            ChannelResponse response = DtoMapper.toChannelResponse(channel);

            assertThat(response.getId()).isEqualTo(id.toHexString());
            assertThat(response.getName()).isEqualTo("general");
            assertThat(response.getType()).isEqualTo("GROUP");
            assertThat(response.getWorkspaceId()).isEqualTo(5);
            assertThat(response.getMembers()).containsExactly(1, 2, 3);
            assertThat(response.getCreatedBy()).isEqualTo(1);
            assertThat(response.getCreatedAt()).isEqualTo(now);
            assertThat(response.getUpdatedAt()).isEqualTo(now);
        }

        @Test
        void shouldHandleDmChannel() {
            ObjectId id = new ObjectId();
            Channel dm = Channel.builder()
                    .id(id)
                    .name(null)
                    .type(ChannelType.DIRECT)
                    .workspaceId(null)
                    .members(List.of(1, 2))
                    .createdBy(1)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            ChannelResponse response = DtoMapper.toChannelResponse(dm);

            assertThat(response.getName()).isNull();
            assertThat(response.getType()).isEqualTo("DIRECT");
            assertThat(response.getWorkspaceId()).isNull();
        }
    }

    // ────────────────────────────────────────
    // toMessageResponse
    // ────────────────────────────────────────

    @Nested
    class ToMessageResponse {

        @Test
        void shouldMapAllFieldsForNormalMessage() {
            ObjectId id = new ObjectId();
            ObjectId channelId = new ObjectId();
            Instant now = Instant.now();
            Message message = Message.builder()
                    .id(id)
                    .channelId(channelId)
                    .senderId(10)
                    .content("hello world")
                    .type(MessageType.TEXT)
                    .threadId(null)
                    .replyToId(null)
                    .mentions(List.of(1, 2))
                    .clientMessageId("uuid-123")
                    .deleted(false)
                    .deletedAt(null)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            MessageResponse response = DtoMapper.toMessageResponse(message);

            assertThat(response.getId()).isEqualTo(id.toHexString());
            assertThat(response.getChannelId()).isEqualTo(channelId.toHexString());
            assertThat(response.getSenderId()).isEqualTo(10);
            assertThat(response.getContent()).isEqualTo("hello world");
            assertThat(response.getType()).isEqualTo("TEXT");
            assertThat(response.getThreadId()).isNull();
            assertThat(response.getReplyToId()).isNull();
            assertThat(response.getMentions()).containsExactly(1, 2);
            assertThat(response.getClientMessageId()).isEqualTo("uuid-123");
            assertThat(response.getDeleted()).isFalse();
            assertThat(response.getDeletedAt()).isNull();
            assertThat(response.getCreatedAt()).isEqualTo(now);
            assertThat(response.getUpdatedAt()).isEqualTo(now);
        }

        @Test
        void shouldMapThreadAndReplyIds() {
            ObjectId id = new ObjectId();
            ObjectId channelId = new ObjectId();
            ObjectId threadId = new ObjectId();
            ObjectId replyToId = new ObjectId();
            Message message = Message.builder()
                    .id(id)
                    .channelId(channelId)
                    .senderId(10)
                    .content("reply in thread")
                    .type(MessageType.TEXT)
                    .threadId(threadId)
                    .replyToId(replyToId)
                    .deleted(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            MessageResponse response = DtoMapper.toMessageResponse(message);

            assertThat(response.getThreadId()).isEqualTo(threadId.toHexString());
            assertThat(response.getReplyToId()).isEqualTo(replyToId.toHexString());
        }

        @Test
        void shouldMapDeletedMessage() {
            Instant now = Instant.now();
            Message message = Message.builder()
                    .id(new ObjectId())
                    .channelId(new ObjectId())
                    .senderId(10)
                    .content(null)
                    .type(MessageType.TEXT)
                    .deleted(true)
                    .deletedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            MessageResponse response = DtoMapper.toMessageResponse(message);

            assertThat(response.getDeleted()).isTrue();
            assertThat(response.getDeletedAt()).isEqualTo(now);
            assertThat(response.getContent()).isNull();
        }

        @Test
        void shouldMapSystemMessage() {
            Message message = Message.builder()
                    .id(new ObjectId())
                    .channelId(new ObjectId())
                    .senderId(0)
                    .content("User joined the channel")
                    .type(MessageType.SYSTEM)
                    .deleted(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            MessageResponse response = DtoMapper.toMessageResponse(message);

            assertThat(response.getType()).isEqualTo("SYSTEM");
        }

        @Test
        void shouldHandleNullMentions() {
            Message message = Message.builder()
                    .id(new ObjectId())
                    .channelId(new ObjectId())
                    .senderId(10)
                    .content("no mentions")
                    .type(MessageType.TEXT)
                    .mentions(null)
                    .deleted(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            MessageResponse response = DtoMapper.toMessageResponse(message);

            assertThat(response.getMentions()).isNull();
        }
    }

    // ────────────────────────────────────────
    // toThreadResponse
    // ────────────────────────────────────────

    @Nested
    class ToThreadResponse {

        @Test
        void shouldMapAllFields() {
            ObjectId id = new ObjectId();
            ObjectId channelId = new ObjectId();
            ObjectId rootMessageId = new ObjectId();
            Instant now = Instant.now();
            ChatThread thread = ChatThread.builder()
                    .id(id)
                    .channelId(channelId)
                    .rootMessageId(rootMessageId)
                    .name("Discussion about feature X")
                    .createdBy(42)
                    .deleted(false)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            ThreadResponse response = DtoMapper.toThreadResponse(thread);

            assertThat(response.getId()).isEqualTo(id.toHexString());
            assertThat(response.getChannelId()).isEqualTo(channelId.toHexString());
            assertThat(response.getRootMessageId()).isEqualTo(rootMessageId.toHexString());
            assertThat(response.getName()).isEqualTo("Discussion about feature X");
            assertThat(response.getCreatedBy()).isEqualTo(42);
            assertThat(response.getDeleted()).isFalse();
            assertThat(response.getCreatedAt()).isEqualTo(now);
            assertThat(response.getUpdatedAt()).isEqualTo(now);
        }

        @Test
        void shouldMapDeletedThread() {
            ChatThread thread = ChatThread.builder()
                    .id(new ObjectId())
                    .channelId(new ObjectId())
                    .rootMessageId(new ObjectId())
                    .name("old thread")
                    .createdBy(1)
                    .deleted(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            ThreadResponse response = DtoMapper.toThreadResponse(thread);

            assertThat(response.getDeleted()).isTrue();
        }

        @Test
        void shouldHandleNullName() {
            ChatThread thread = ChatThread.builder()
                    .id(new ObjectId())
                    .channelId(new ObjectId())
                    .rootMessageId(new ObjectId())
                    .name(null)
                    .createdBy(1)
                    .deleted(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            ThreadResponse response = DtoMapper.toThreadResponse(thread);

            assertThat(response.getName()).isNull();
        }
    }
}
