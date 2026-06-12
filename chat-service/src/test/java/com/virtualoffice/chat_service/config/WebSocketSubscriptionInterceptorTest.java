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
package com.virtualoffice.chat_service.config;

import com.virtualoffice.chat_service.dto.response.WebSocketEvent;
import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChannelType;
import com.virtualoffice.chat_service.model.ChatThread;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import com.virtualoffice.chat_service.repository.ThreadRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketSubscriptionInterceptorTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketSubscriptionInterceptor interceptor;

    private void setup() {
        interceptor = new WebSocketSubscriptionInterceptor(channelRepository, threadRepository, messagingTemplate);
    }

    private Message<?> buildSubscribeMessage(String destination, Integer userId) {
        return buildSubscribeMessage(destination, userId, "session-1");
    }

    private Message<?> buildSubscribeMessage(String destination, Integer userId, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (userId != null) {
            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("userId", userId);
            accessor.setSessionAttributes(sessionAttrs);
        }
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId("sub-1");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> buildNonSubscribeMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat/send");
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("userId", 10);
        accessor.setSessionAttributes(sessionAttrs);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void assertErrorEnvelope(String expectedCode, String expectedSession) {
        ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq(expectedSession), eq("/queue/errors"), captor.capture());
        WebSocketEvent<?> event = captor.getValue();
        assertThat(event.getAction()).isEqualTo("ERROR");
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) event.getPayload();
        assertThat(payload.get("code")).isEqualTo(expectedCode);
        assertThat(payload.get("message")).isNotBlank();
    }

    // ────────────────────────────────────────
    // pass-through for non-SUBSCRIBE frames
    // ────────────────────────────────────────

    @Nested
    class PassThrough {

        @Test
        void shouldPassThroughNonSubscribeMessages() {
            setup();
            Message<?> msg = buildNonSubscribeMessage();

            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNotNull();
            verifyNoInteractions(channelRepository);
            verifyNoInteractions(threadRepository);
            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void shouldPassThroughWhenDestinationIsNull() {
            setup();
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination(null);
            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("userId", 10);
            accessor.setSessionAttributes(sessionAttrs);
            accessor.setSubscriptionId("sub-1");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNotNull();
            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void shouldPassThroughForUnrelatedDestinations() {
            setup();
            Message<?> msg = buildSubscribeMessage("/user/queue/errors", 10);

            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNotNull();
            verifyNoInteractions(channelRepository);
            verifyNoInteractions(messagingTemplate);
        }
    }

    // ────────────────────────────────────────
    // channel subscription validation
    // ────────────────────────────────────────

    @Nested
    class ChannelSubscription {

        @Test
        void shouldAllowMemberToSubscribe() {
            setup();
            ObjectId channelId = new ObjectId();
            Channel channel = Channel.builder()
                    .id(channelId)
                    .type(ChannelType.GROUP)
                    .members(List.of(10, 20))
                    .build();
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));

            Message<?> msg = buildSubscribeMessage("/topic/channel/" + channelId.toHexString(), 10);
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNotNull();
            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void shouldAllowMemberToSubscribeToTyping() {
            setup();
            ObjectId channelId = new ObjectId();
            Channel channel = Channel.builder()
                    .id(channelId)
                    .type(ChannelType.GROUP)
                    .members(List.of(10))
                    .build();
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));

            Message<?> msg = buildSubscribeMessage("/topic/channel/" + channelId.toHexString() + "/typing", 10);
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNotNull();
            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void shouldRejectNonMemberWithErrorEnvelope() {
            setup();
            ObjectId channelId = new ObjectId();
            Channel channel = Channel.builder()
                    .id(channelId)
                    .type(ChannelType.GROUP)
                    .members(List.of(20, 30))
                    .build();
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));

            Message<?> msg = buildSubscribeMessage("/topic/channel/" + channelId.toHexString(), 10, "sess-A");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            // SUBSCRIBE is dropped, error sent to /user/queue/errors
            assertThat(result).isNull();
            assertErrorEnvelope("NOT_A_MEMBER", "sess-A");
        }

        @Test
        void shouldRejectMissingChannelWithErrorEnvelope() {
            setup();
            ObjectId channelId = new ObjectId();
            when(channelRepository.findById(channelId)).thenReturn(Optional.empty());

            Message<?> msg = buildSubscribeMessage("/topic/channel/" + channelId.toHexString(), 10, "sess-B");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNull();
            assertErrorEnvelope("CHANNEL_NOT_FOUND", "sess-B");
        }

        @Test
        void shouldRejectMalformedIdWithInvalidPayload() {
            setup();

            Message<?> msg = buildSubscribeMessage("/topic/channel/not-a-hex", 10, "sess-C");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNull();
            assertErrorEnvelope("INVALID_PAYLOAD", "sess-C");
            verifyNoInteractions(channelRepository);
        }

        @Test
        void shouldRejectEmptyIdSegmentWithInvalidPayload() {
            setup();

            Message<?> msg = buildSubscribeMessage("/topic/channel/", 10, "sess-D");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNull();
            assertErrorEnvelope("INVALID_PAYLOAD", "sess-D");
        }

        @Test
        void shouldDropMessageSilentlyWhenSessionHasNoUserId() {
            setup();
            ObjectId channelId = new ObjectId();
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination("/topic/channel/" + channelId.toHexString());
            accessor.setSessionAttributes(null);
            accessor.setSessionId("sess-X");
            accessor.setSubscriptionId("sub-1");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(msg, messageChannel);

            // no userId means we can't address /user — drop silently, no error sent
            assertThat(result).isNull();
            verifyNoInteractions(messagingTemplate);
        }
    }

    // ────────────────────────────────────────
    // SEND frame authorization (clients may only send to /app/**)
    // ────────────────────────────────────────

    @Nested
    class SendAuthorization {

        private Message<?> buildSendMessage(String destination, String sessionId) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
            accessor.setDestination(destination);
            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("userId", 10);
            accessor.setSessionAttributes(sessionAttrs);
            accessor.setSessionId(sessionId);
            return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }

        @Test
        void shouldRejectSendToTopicDestination() {
            setup();
            Message<?> msg = buildSendMessage("/topic/channel/" + new ObjectId().toHexString(), "sess-send");

            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNull();
            assertErrorEnvelope("INVALID_PAYLOAD", "sess-send");
        }

        @Test
        void shouldRejectSendToQueueDestination() {
            setup();
            Message<?> msg = buildSendMessage("/queue/errors", "sess-q");

            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNull();
        }

        @Test
        void shouldAllowSendToAppDestination() {
            setup();
            Message<?> msg = buildSendMessage("/app/chat/send", "sess-app");

            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNotNull();
            verifyNoInteractions(messagingTemplate);
        }
    }

    // ────────────────────────────────────────
    // thread subscription validation
    // ────────────────────────────────────────

    @Nested
    class ThreadSubscription {

        @Test
        void shouldAllowMemberToSubscribeToThread() {
            setup();
            ObjectId threadId = new ObjectId();
            ObjectId channelId = new ObjectId();
            ChatThread thread = ChatThread.builder()
                    .id(threadId)
                    .channelId(channelId)
                    .build();
            Channel channel = Channel.builder()
                    .id(channelId)
                    .type(ChannelType.GROUP)
                    .members(List.of(10))
                    .build();
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(thread));
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));

            Message<?> msg = buildSubscribeMessage("/topic/thread/" + threadId.toHexString(), 10);
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNotNull();
            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void shouldAllowMemberToSubscribeToThreadTyping() {
            setup();
            ObjectId threadId = new ObjectId();
            ObjectId channelId = new ObjectId();
            ChatThread thread = ChatThread.builder()
                    .id(threadId)
                    .channelId(channelId)
                    .build();
            Channel channel = Channel.builder()
                    .id(channelId)
                    .type(ChannelType.GROUP)
                    .members(List.of(10))
                    .build();
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(thread));
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));

            Message<?> msg = buildSubscribeMessage("/topic/thread/" + threadId.toHexString() + "/typing", 10);
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNotNull();
            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void shouldRejectMissingThreadWithErrorEnvelope() {
            setup();
            ObjectId threadId = new ObjectId();
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.empty());

            Message<?> msg = buildSubscribeMessage("/topic/thread/" + threadId.toHexString(), 10, "sess-T1");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNull();
            assertErrorEnvelope("CHANNEL_NOT_FOUND", "sess-T1");
        }

        @Test
        void shouldRejectNonMemberOfParentChannelWithErrorEnvelope() {
            setup();
            ObjectId threadId = new ObjectId();
            ObjectId channelId = new ObjectId();
            ChatThread thread = ChatThread.builder()
                    .id(threadId)
                    .channelId(channelId)
                    .build();
            Channel channel = Channel.builder()
                    .id(channelId)
                    .type(ChannelType.GROUP)
                    .members(List.of(20, 30))
                    .build();
            when(threadRepository.findActiveById(threadId)).thenReturn(Optional.of(thread));
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));

            Message<?> msg = buildSubscribeMessage("/topic/thread/" + threadId.toHexString(), 10, "sess-T2");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNull();
            assertErrorEnvelope("NOT_A_MEMBER", "sess-T2");
        }

        @Test
        void shouldRejectMalformedThreadId() {
            setup();

            Message<?> msg = buildSubscribeMessage("/topic/thread/garbage", 10, "sess-T3");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertThat(result).isNull();
            assertErrorEnvelope("INVALID_PAYLOAD", "sess-T3");
            verifyNoInteractions(threadRepository);
        }
    }
}
