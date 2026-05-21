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
import com.virtualoffice.chat_service.model.ChatThread;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import com.virtualoffice.chat_service.repository.ThreadRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
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
import java.util.Optional;

// authorization gate for SUBSCRIBE frames. Rejections route an ERROR envelope to
// /user/queue/errors and drop the frame by returning null, so the client always
// gets feedback instead of a silent disconnect.
@Slf4j
@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private final ChannelRepository channelRepository;
    private final ThreadRepository threadRepository;
    // @Lazy breaks the bean cycle: SimpMessagingTemplate → clientInboundChannel → this interceptor
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketSubscriptionInterceptor(ChannelRepository channelRepository,
                                            ThreadRepository threadRepository,
                                            @Lazy SimpMessagingTemplate messagingTemplate) {
        this.channelRepository = channelRepository;
        this.threadRepository = threadRepository;
        this.messagingTemplate = messagingTemplate;
    }

    static final String CODE_NOT_A_MEMBER = "NOT_A_MEMBER";
    static final String CODE_CHANNEL_NOT_FOUND = "CHANNEL_NOT_FOUND";
    static final String CODE_INVALID_PAYLOAD = "INVALID_PAYLOAD";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Integer userId = getUserIdFromSession(accessor);
        if (userId == null) {
            // can't address /user without a userId — drop silently
            log.warn("subscription with no userId in session, dropping");
            return null;
        }

        try {
            if (destination.startsWith("/topic/channel/")) {
                validateChannelMembership(extractId(destination, "/topic/channel/"), userId);
            }
            if (destination.startsWith("/topic/thread/")) {
                validateThreadAccess(extractId(destination, "/topic/thread/"), userId);
            }
        } catch (SubscriptionDenied denied) {
            sendError(accessor, denied.code, denied.getMessage());
            return null;
        } catch (IllegalArgumentException malformed) {
            // malformed ObjectId or empty path segment
            sendError(accessor, CODE_INVALID_PAYLOAD, malformed.getMessage());
            return null;
        }

        return message;
    }

    private void validateChannelMembership(String channelId, Integer userId) {
        ObjectId oid;
        try {
            oid = new ObjectId(channelId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid channel id: " + channelId);
        }
        Optional<Channel> channelOpt = channelRepository.findById(oid);
        if (channelOpt.isEmpty()) {
            throw new SubscriptionDenied(CODE_CHANNEL_NOT_FOUND, "channel not found: " + channelId);
        }
        if (!channelOpt.get().getMembers().contains(userId)) {
            throw new SubscriptionDenied(CODE_NOT_A_MEMBER, "not a member of channel: " + channelId);
        }
    }

    private void validateThreadAccess(String threadId, Integer userId) {
        ObjectId oid;
        try {
            oid = new ObjectId(threadId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid thread id: " + threadId);
        }
        Optional<ChatThread> threadOpt = threadRepository.findActiveById(oid);
        if (threadOpt.isEmpty()) {
            throw new SubscriptionDenied(CODE_CHANNEL_NOT_FOUND, "thread not found: " + threadId);
        }
        validateChannelMembership(threadOpt.get().getChannelId().toHexString(), userId);
    }

    // also handles the /typing suffix variant
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
        if (sessionAttrs == null) return null;
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

    // private signal type — caught in preSend and translated into an ERROR envelope
    private static final class SubscriptionDenied extends RuntimeException {
        final String code;

        SubscriptionDenied(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
