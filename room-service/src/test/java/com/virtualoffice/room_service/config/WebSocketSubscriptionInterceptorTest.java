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
package com.virtualoffice.room_service.config;

import com.virtualoffice.room_service.dto.response.WebSocketEvent;
import com.virtualoffice.room_service.service.RoomService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketSubscriptionInterceptorTest {

    @Mock
    private RoomService roomService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketSubscriptionInterceptor interceptor;

    private void setup() {
        interceptor = new WebSocketSubscriptionInterceptor(roomService, messagingTemplate);
    }

    private Message<?> frame(StompCommand command, String destination, Integer userId, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (userId != null) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("userId", userId);
            accessor.setSessionAttributes(attrs);
        }
        accessor.setSessionId(sessionId);
        if (command == StompCommand.SUBSCRIBE) {
            accessor.setSubscriptionId("sub-1");
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void assertError(String code, String session) {
        ArgumentCaptor<WebSocketEvent> captor = ArgumentCaptor.forClass(WebSocketEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq(session), eq("/queue/errors"), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) captor.getValue().getPayload();
        assertThat(payload.get("code")).isEqualTo(code);
    }

    @Test
    void shouldAllowSendToAppDestination() {
        setup();
        Message<?> result = interceptor.preSend(frame(StompCommand.SEND, "/app/room/state", 10, "s1"), messageChannel);
        assertThat(result).isNotNull();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void shouldRejectSendToTopicDestination() {
        setup();
        Message<?> result = interceptor.preSend(frame(StompCommand.SEND, "/topic/room/r1", 10, "s1"), messageChannel);
        assertThat(result).isNull();
        assertError("INVALID_PAYLOAD", "s1");
    }

    @Test
    void shouldAllowMemberToSubscribe() {
        setup();
        when(roomService.isMember("r1", 10)).thenReturn(true);
        Message<?> result = interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room/r1", 10, "s1"), messageChannel);
        assertThat(result).isNotNull();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void shouldRejectNonMemberSubscribe() {
        setup();
        when(roomService.isMember("r1", 10)).thenReturn(false);
        Message<?> result = interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room/r1", 10, "s2"), messageChannel);
        assertThat(result).isNull();
        assertError("NOT_A_MEMBER", "s2");
    }

    @Test
    void shouldDropSubscribeWithoutUserId() {
        setup();
        Message<?> result = interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room/r1", null, "s3"), messageChannel);
        assertThat(result).isNull();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void shouldRejectMalformedRoomId() {
        setup();
        when(roomService.isMember("bad", 10)).thenThrow(new IllegalArgumentException("invalid id"));
        Message<?> result = interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room/bad", 10, "s4"), messageChannel);
        assertThat(result).isNull();
        assertError("INVALID_PAYLOAD", "s4");
    }

    @Test
    void shouldPassThroughUnrelatedSubscribe() {
        setup();
        Message<?> result = interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/errors", 10, "s5"), messageChannel);
        assertThat(result).isNotNull();
        verifyNoInteractions(roomService);
        verifyNoInteractions(messagingTemplate);
    }
}
