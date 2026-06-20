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
package com.virtualoffice.room_service.controller;

import com.virtualoffice.room_service.dto.request.StompHeartbeat;
import com.virtualoffice.room_service.dto.request.StompRoomStateEvent;
import com.virtualoffice.room_service.dto.response.ParticipantResponse;
import com.virtualoffice.room_service.dto.response.WebSocketEvent;
import com.virtualoffice.room_service.service.PresenceService;
import com.virtualoffice.room_service.service.RoomPushService;
import com.virtualoffice.room_service.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomStompController {

    private final RoomService roomService;
    private final PresenceService presenceService;
    private final RoomPushService roomPushService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/room/state")
    public void handleState(StompRoomStateEvent payload, SimpMessageHeaderAccessor headerAccessor) {
        Integer userId = readUserId(headerAccessor);
        if (userId == null) {
            sendErrorToUser(headerAccessor, "INTERNAL_ERROR", "missing session identity");
            return;
        }
        if (payload.getRoomId() == null) {
            sendErrorToUser(headerAccessor, "INVALID_PAYLOAD", "roomId is required");
            return;
        }

        try {
            if (!roomService.isMember(payload.getRoomId(), userId)) {
                return;
            }
            ParticipantResponse updated = presenceService.updateState(
                    payload.getRoomId(), userId,
                    payload.isMuted(), payload.isCameraOn(), payload.isScreenSharing());
            if (updated != null) {
                roomPushService.stateChanged(payload.getRoomId(), updated);
            }
        } catch (IllegalArgumentException e) {
            sendErrorToUser(headerAccessor, "INVALID_PAYLOAD", e.getMessage());
        } catch (Exception e) {
            log.error("error handling room state: {}", e.getMessage(), e);
            sendErrorToUser(headerAccessor, "INTERNAL_ERROR", "failed to update state");
        }
    }

    @MessageMapping("/room/heartbeat")
    public void handleHeartbeat(StompHeartbeat payload, SimpMessageHeaderAccessor headerAccessor) {
        Integer userId = readUserId(headerAccessor);
        if (userId == null || payload.getRoomId() == null) {
            return;
        }
        try {
            presenceService.heartbeat(payload.getRoomId(), userId);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public WebSocketEvent<Map<String, String>> handleException(Exception e) {
        log.warn("unhandled room STOMP exception: {}", e.getMessage());
        return WebSocketEvent.of("ERROR", Map.of(
                "code", "INTERNAL_ERROR",
                "message", e.getMessage() != null ? e.getMessage() : "unexpected error"
        ));
    }

    private Integer readUserId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs == null) {
            return null;
        }
        return (Integer) sessionAttrs.get("userId");
    }

    private void sendErrorToUser(SimpMessageHeaderAccessor headerAccessor, String code, String message) {
        WebSocketEvent<Map<String, String>> error = WebSocketEvent.of(
                "ERROR",
                Map.of(
                        "code", code,
                        "message", message != null ? message : "unknown error"
                )
        );
        messagingTemplate.convertAndSendToUser(headerAccessor.getSessionId(), "/queue/errors", error);
    }
}
