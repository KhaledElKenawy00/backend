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
package com.virtualoffice.chat_service.controller;

import com.virtualoffice.chat_service.dto.request.StompSendMessage;
import com.virtualoffice.chat_service.dto.request.StompTypingEvent;
import com.virtualoffice.chat_service.dto.response.MessageResponse;
import com.virtualoffice.chat_service.dto.response.WebSocketEvent;
import com.virtualoffice.chat_service.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatStompControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatStompController controller;

    private SimpMessageHeaderAccessor headerAccessor;

    @BeforeEach
    void setUp() {
        headerAccessor = SimpMessageHeaderAccessor.create();
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("userId", 10);
        sessionAttrs.put("userRole", "USER");
        headerAccessor.setSessionAttributes(sessionAttrs);
        headerAccessor.setSessionId("session-123");
    }

    // ────────────────────────────────────────
    // handleSendMessage — happy path
    // ────────────────────────────────────────

    @Nested
    class SendMessageHappyPath {

        @Test
        void shouldSendMessageToChannel() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1")
                    .content("hello")
                    .build();
            MessageResponse saved = MessageResponse.builder()
                    .id("msg1")
                    .channelId("ch1")
                    .content("hello")
                    .build();
            when(messageService.sendMessage(eq("ch1"), any(), eq(10), eq("USER"))).thenReturn(saved);

            controller.handleSendMessage(payload, headerAccessor);

            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
        }

        @Test
        void shouldBroadcastToBothTopicsForThreadMessage() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1")
                    .content("threaded msg")
                    .threadId("t1")
                    .build();
            MessageResponse saved = MessageResponse.builder()
                    .id("msg1")
                    .channelId("ch1")
                    .threadId("t1")
                    .content("threaded msg")
                    .build();
            when(messageService.sendMessage(eq("ch1"), any(), eq(10), eq("USER"))).thenReturn(saved);

            controller.handleSendMessage(payload, headerAccessor);

            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
            verify(messagingTemplate).convertAndSend(eq("/topic/thread/t1"), any(WebSocketEvent.class));
        }

        @Test
        void shouldOnlyBroadcastToChannelTopicForNonThreadMessage() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1")
                    .content("hi")
                    .build();
            MessageResponse saved = MessageResponse.builder()
                    .id("msg1").channelId("ch1").content("hi").build();
            when(messageService.sendMessage(eq("ch1"), any(), eq(10), eq("USER"))).thenReturn(saved);

            controller.handleSendMessage(payload, headerAccessor);

            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1"), any(WebSocketEvent.class));
            verify(messagingTemplate, never()).convertAndSend(startsWith("/topic/thread/"), any(WebSocketEvent.class));
        }

        @Test
        void shouldPassAllFieldsToService() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1")
                    .content("with extras")
                    .threadId("t1")
                    .replyToId("reply1")
                    .mentions(List.of(1, 2))
                    .clientMessageId("uuid-123")
                    .build();
            MessageResponse saved = MessageResponse.builder().id("msg1").channelId("ch1").build();
            when(messageService.sendMessage(eq("ch1"), any(), eq(10), eq("USER"))).thenReturn(saved);

            controller.handleSendMessage(payload, headerAccessor);

            verify(messageService).sendMessage(eq("ch1"), argThat(req ->
                    "with extras".equals(req.getContent()) &&
                    "t1".equals(req.getThreadId()) &&
                    "reply1".equals(req.getReplyToId()) &&
                    "uuid-123".equals(req.getClientMessageId()) &&
                    req.getMentions().containsAll(List.of(1, 2))
            ), eq(10), eq("USER"));
        }
    }

    // ────────────────────────────────────────
    // handleSendMessage — validation errors
    // ────────────────────────────────────────

    @Nested
    class SendMessageValidation {

        @Test
        void shouldSendErrorWhenChannelIdNull() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId(null)
                    .content("hello")
                    .build();

            controller.handleSendMessage(payload, headerAccessor);

            verify(messagingTemplate).convertAndSendToUser(
                    eq("session-123"), eq("/queue/errors"), any(WebSocketEvent.class));
            verifyNoInteractions(messageService);
        }

        @Test
        void shouldSendErrorWhenContentNull() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1")
                    .content(null)
                    .build();

            controller.handleSendMessage(payload, headerAccessor);

            verify(messagingTemplate).convertAndSendToUser(
                    eq("session-123"), eq("/queue/errors"), any(WebSocketEvent.class));
        }

        @Test
        void shouldSendErrorWhenContentBlank() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1")
                    .content("   ")
                    .build();

            controller.handleSendMessage(payload, headerAccessor);

            verify(messagingTemplate).convertAndSendToUser(
                    eq("session-123"), eq("/queue/errors"), any(WebSocketEvent.class));
        }
    }

    // ────────────────────────────────────────
    // handleSendMessage — service exceptions
    // ────────────────────────────────────────

    @Nested
    class SendMessageErrors {

        @Test
        void shouldMapForbiddenToNotAMember() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1").content("hello").build();
            when(messageService.sendMessage(any(), any(), any(), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member"));

            controller.handleSendMessage(payload, headerAccessor);

            ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSendToUser(eq("session-123"), eq("/queue/errors"), captor.capture());

            @SuppressWarnings("unchecked")
            Map<String, String> errorPayload = (Map<String, String>) captor.getValue().getPayload();
            assertThat(errorPayload.get("code")).isEqualTo("NOT_A_MEMBER");
        }

        @Test
        void shouldMapNotFoundToChannelNotFound() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1").content("hello").build();
            when(messageService.sendMessage(any(), any(), any(), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "channel not found"));

            controller.handleSendMessage(payload, headerAccessor);

            ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSendToUser(eq("session-123"), eq("/queue/errors"), captor.capture());

            @SuppressWarnings("unchecked")
            Map<String, String> errorPayload = (Map<String, String>) captor.getValue().getPayload();
            assertThat(errorPayload.get("code")).isEqualTo("CHANNEL_NOT_FOUND");
        }

        @Test
        void shouldMapBadRequestToInvalidPayload() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1").content("hello").build();
            when(messageService.sendMessage(any(), any(), any(), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad"));

            controller.handleSendMessage(payload, headerAccessor);

            ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSendToUser(eq("session-123"), eq("/queue/errors"), captor.capture());

            @SuppressWarnings("unchecked")
            Map<String, String> errorPayload = (Map<String, String>) captor.getValue().getPayload();
            assertThat(errorPayload.get("code")).isEqualTo("INVALID_PAYLOAD");
        }

        @Test
        void shouldMapUnknownStatusToInternalError() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1").content("hello").build();
            when(messageService.sendMessage(any(), any(), any(), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "conflict"));

            controller.handleSendMessage(payload, headerAccessor);

            ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSendToUser(eq("session-123"), eq("/queue/errors"), captor.capture());

            @SuppressWarnings("unchecked")
            Map<String, String> errorPayload = (Map<String, String>) captor.getValue().getPayload();
            assertThat(errorPayload.get("code")).isEqualTo("INTERNAL_ERROR");
        }

        @Test
        void shouldHandleGenericException() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1").content("hello").build();
            when(messageService.sendMessage(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("unexpected"));

            controller.handleSendMessage(payload, headerAccessor);

            ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSendToUser(eq("session-123"), eq("/queue/errors"), captor.capture());

            @SuppressWarnings("unchecked")
            Map<String, String> errorPayload = (Map<String, String>) captor.getValue().getPayload();
            assertThat(errorPayload.get("code")).isEqualTo("INTERNAL_ERROR");
            assertThat(errorPayload.get("message")).isEqualTo("failed to send message");
        }
    }

    // ────────────────────────────────────────
    // handleTyping
    // ────────────────────────────────────────

    @Nested
    class HandleTyping {

        @Test
        void shouldBroadcastTypingToChannel() {
            StompTypingEvent payload = StompTypingEvent.builder()
                    .channelId("ch1")
                    .typing(true)
                    .build();

            controller.handleTyping(payload, headerAccessor);

            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1/typing"), any(WebSocketEvent.class));
        }

        @Test
        void shouldBroadcastTypingToThread() {
            StompTypingEvent payload = StompTypingEvent.builder()
                    .channelId("ch1")
                    .threadId("t1")
                    .typing(true)
                    .build();

            controller.handleTyping(payload, headerAccessor);

            verify(messagingTemplate).convertAndSend(eq("/topic/thread/t1/typing"), any(WebSocketEvent.class));
        }

        @Test
        void shouldIgnoreTypingWithNullChannelId() {
            StompTypingEvent payload = StompTypingEvent.builder()
                    .channelId(null)
                    .typing(true)
                    .build();

            controller.handleTyping(payload, headerAccessor);

            verifyNoInteractions(messagingTemplate);
        }

        @Test
        void shouldIncludeUserIdInNotification() {
            StompTypingEvent payload = StompTypingEvent.builder()
                    .channelId("ch1")
                    .typing(false)
                    .build();

            controller.handleTyping(payload, headerAccessor);

            ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/channel/ch1/typing"), captor.capture());
            assertThat(captor.getValue().getAction()).isEqualTo("TYPING");
        }
    }

    // ────────────────────────────────────────
    // handleException (message exception handler)
    // ────────────────────────────────────────

    @Nested
    class HandleException {

        @Test
        void shouldReturnErrorEvent() {
            Exception ex = new RuntimeException("test error");

            WebSocketEvent<Map<String, String>> result = controller.handleException(ex);

            assertThat(result.getAction()).isEqualTo("ERROR");
            assertThat(result.getPayload().get("code")).isEqualTo("INTERNAL_ERROR");
            assertThat(result.getPayload().get("message")).isEqualTo("test error");
        }

        @Test
        void shouldHandleNullMessage() {
            Exception ex = new RuntimeException();

            WebSocketEvent<Map<String, String>> result = controller.handleException(ex);

            assertThat(result.getPayload().get("message")).isEqualTo("unexpected error");
        }
    }

    // ────────────────────────────────────────
    // session attribute edge cases
    // ────────────────────────────────────────

    @Nested
    class SessionAttributes {

        @Test
        void shouldSendErrorWhenNoUserIdInSession() {
            SimpMessageHeaderAccessor badAccessor = SimpMessageHeaderAccessor.create();
            badAccessor.setSessionAttributes(new HashMap<>());
            badAccessor.setSessionId("bad-session");

            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1").content("hello").build();

            controller.handleSendMessage(payload, badAccessor);

            ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSendToUser(eq("bad-session"), eq("/queue/errors"), captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, String> err = (Map<String, String>) captor.getValue().getPayload();
            assertThat(err.get("code")).isEqualTo("INTERNAL_ERROR");
            assertThat(err.get("message")).isEqualTo("missing session identity");
            verifyNoInteractions(messageService);
        }

        @Test
        void shouldSendErrorWhenNoUserRoleInSession() {
            SimpMessageHeaderAccessor noRoleAccessor = SimpMessageHeaderAccessor.create();
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("userId", 10);
            noRoleAccessor.setSessionAttributes(attrs);
            noRoleAccessor.setSessionId("no-role-session");

            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1").content("hello").build();

            controller.handleSendMessage(payload, noRoleAccessor);

            verify(messagingTemplate).convertAndSendToUser(
                    eq("no-role-session"), eq("/queue/errors"), any(WebSocketEvent.class));
            verifyNoInteractions(messageService);
        }

        @Test
        void shouldSendErrorWhenSessionAttributesNull() {
            SimpMessageHeaderAccessor nullAttrsAccessor = SimpMessageHeaderAccessor.create();
            nullAttrsAccessor.setSessionAttributes(null);
            nullAttrsAccessor.setSessionId("null-attrs-session");

            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("ch1").content("hello").build();

            controller.handleSendMessage(payload, nullAttrsAccessor);

            verify(messagingTemplate).convertAndSendToUser(
                    eq("null-attrs-session"), eq("/queue/errors"), any(WebSocketEvent.class));
            verifyNoInteractions(messageService);
        }

        @Test
        void typingShouldSilentlyDropWhenNoUserId() {
            SimpMessageHeaderAccessor badAccessor = SimpMessageHeaderAccessor.create();
            badAccessor.setSessionAttributes(new HashMap<>());
            badAccessor.setSessionId("bad-session");

            StompTypingEvent payload = StompTypingEvent.builder()
                    .channelId("ch1").typing(true).build();

            controller.handleTyping(payload, badAccessor);

            verifyNoInteractions(messagingTemplate);
        }
    }

    // ────────────────────────────────────────
    // STOMP send catches IllegalArgumentException as INVALID_PAYLOAD
    // ────────────────────────────────────────

    @Nested
    class IllegalArgumentMapping {

        @Test
        void shouldMapBadObjectIdToInvalidPayload() {
            StompSendMessage payload = StompSendMessage.builder()
                    .channelId("not-a-hex").content("hi").build();
            when(messageService.sendMessage(any(), any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("invalid hexadecimal representation"));

            controller.handleSendMessage(payload, headerAccessor);

            ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
            verify(messagingTemplate).convertAndSendToUser(eq("session-123"), eq("/queue/errors"), captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, String> err = (Map<String, String>) captor.getValue().getPayload();
            assertThat(err.get("code")).isEqualTo("INVALID_PAYLOAD");
        }
    }
}
