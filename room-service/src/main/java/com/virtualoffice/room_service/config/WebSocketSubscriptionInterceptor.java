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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketSubscriptionInterceptor(RoomService roomService,
                                            @Lazy SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    static final String CODE_NOT_A_MEMBER = "NOT_A_MEMBER";
    static final String CODE_INVALID_PAYLOAD = "INVALID_PAYLOAD";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (command == StompCommand.SEND) {
            String sendDestination = accessor.getDestination();
            if (sendDestination == null || !sendDestination.startsWith("/app/")) {
                sendError(accessor, CODE_INVALID_PAYLOAD, "cannot send to destination: " + sendDestination);
                return null;
            }
            return message;
        }

        if (command != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Integer userId = getUserIdFromSession(accessor);
        if (userId == null) {
            log.warn("subscription with no userId in session, dropping");
            return null;
        }

        if (destination.startsWith("/topic/room/")) {
            try {
                String roomId = extractId(destination, "/topic/room/");
                if (!roomService.isMember(roomId, userId)) {
                    sendError(accessor, CODE_NOT_A_MEMBER, "not a member of room: " + roomId);
                    return null;
                }
            } catch (IllegalArgumentException e) {
                sendError(accessor, CODE_INVALID_PAYLOAD, e.getMessage());
                return null;
            }
        }

        return message;
    }

    private String extractId(String destination, String prefix) {
        String remainder = destination.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        String id = slashIndex > 0 ? remainder.substring(0, slashIndex) : remainder;
        if (id.isEmpty()) {
            throw new IllegalArgumentException("missing id in destination: " + destination);
        }
        return id;
    }

    private Integer getUserIdFromSession(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null) {
            return null;
        }
        return (Integer) sessionAttrs.get("userId");
    }

    private void sendError(StompHeaderAccessor accessor, String code, String message) {
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            log.warn("cannot send subscription error — no sessionId on frame ({})", code);
            return;
        }
        WebSocketEvent<Map<String, String>> event = WebSocketEvent.of(
                "ERROR",
                Map.of(
                        "code", code,
                        "message", message != null ? message : "subscription denied"
                )
        );
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", event);
    }
}
