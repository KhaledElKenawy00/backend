package com.virtualoffice.chat_service.controller;

import com.virtualoffice.chat_service.dto.request.SendMessageRequest;
import com.virtualoffice.chat_service.dto.request.StompSendMessage;
import com.virtualoffice.chat_service.dto.request.StompTypingEvent;
import com.virtualoffice.chat_service.dto.response.MessageResponse;
import com.virtualoffice.chat_service.dto.response.TypingNotification;
import com.virtualoffice.chat_service.dto.response.WebSocketEvent;
import com.virtualoffice.chat_service.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/send")
    public void handleSendMessage(StompSendMessage payload, SimpMessageHeaderAccessor headerAccessor) {
        Integer userId = readUserId(headerAccessor);
        String role = readUserRole(headerAccessor);
        if (userId == null || role == null) {
            // handshake interceptor should have set these — if they're missing the session is broken
            sendErrorToUser(headerAccessor, "INTERNAL_ERROR", "missing session identity");
            return;
        }

        if (payload.getChannelId() == null || payload.getContent() == null || payload.getContent().isBlank()) {
            sendErrorToUser(headerAccessor, "INVALID_PAYLOAD", "channelId and content are required");
            return;
        }

        try {
            SendMessageRequest request = SendMessageRequest.builder()
                    .content(payload.getContent())
                    .threadId(payload.getThreadId())
                    .replyToId(payload.getReplyToId())
                    .mentions(payload.getMentions())
                    .clientMessageId(payload.getClientMessageId())
                    .build();

            MessageResponse saved = messageService.sendMessage(payload.getChannelId(), request, userId, role);
            WebSocketEvent<MessageResponse> event = WebSocketEvent.of(WebSocketEvent.NEW_MESSAGE, saved);

            // thread messages broadcast to both topics so channel subscribers see thread activity too
            messagingTemplate.convertAndSend("/topic/channel/" + payload.getChannelId(), event);
            if (payload.getThreadId() != null) {
                messagingTemplate.convertAndSend("/topic/thread/" + payload.getThreadId(), event);
            }
        } catch (ResponseStatusException e) {
            sendErrorToUser(headerAccessor, mapStatusToCode(e), e.getReason());
        } catch (IllegalArgumentException e) {
            // malformed ObjectId on channelId/threadId/replyToId
            sendErrorToUser(headerAccessor, "INVALID_PAYLOAD", e.getMessage());
        } catch (Exception e) {
            log.error("error handling STOMP send: {}", e.getMessage(), e);
            sendErrorToUser(headerAccessor, "INTERNAL_ERROR", "failed to send message");
        }
    }

    @MessageMapping("/chat/typing")
    public void handleTyping(StompTypingEvent payload, SimpMessageHeaderAccessor headerAccessor) {
        Integer userId = readUserId(headerAccessor);
        if (userId == null || payload.getChannelId() == null) {
            return;
        }

        TypingNotification notification = TypingNotification.builder()
                .userId(userId)
                .channelId(payload.getChannelId())
                .threadId(payload.getThreadId())
                .typing(payload.isTyping())
                .build();

        WebSocketEvent<TypingNotification> event = WebSocketEvent.of(WebSocketEvent.TYPING, notification);

        if (payload.getThreadId() != null) {
            messagingTemplate.convertAndSend("/topic/thread/" + payload.getThreadId() + "/typing", event);
        } else {
            messagingTemplate.convertAndSend("/topic/channel/" + payload.getChannelId() + "/typing", event);
        }
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public WebSocketEvent<Map<String, String>> handleException(Exception e) {
        log.warn("unhandled STOMP exception: {}", e.getMessage());
        return WebSocketEvent.of("ERROR", Map.of(
                "code", "INTERNAL_ERROR",
                "message", e.getMessage() != null ? e.getMessage() : "unexpected error"
        ));
    }

    // returns null if the session is missing identity — callers emit an error envelope instead of throwing
    private Integer readUserId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs == null) return null;
        return (Integer) sessionAttrs.get("userId");
    }

    private String readUserRole(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs == null) return null;
        return (String) sessionAttrs.get("userRole");
    }

    private void sendErrorToUser(SimpMessageHeaderAccessor headerAccessor, String code, String message) {
        WebSocketEvent<Map<String, String>> error = WebSocketEvent.of(
                "ERROR",
                Map.of(
                        "code", code,
                        "message", message != null ? message : "unknown error"
                )
        );
        // resolves to /user/{sessionId}/queue/errors — matches the @SendToUser path used by handleException
        messagingTemplate.convertAndSendToUser(headerAccessor.getSessionId(), "/queue/errors", error);
    }

    private String mapStatusToCode(ResponseStatusException e) {
        return switch (e.getStatusCode().value()) {
            case 403 -> "NOT_A_MEMBER";
            case 404 -> "CHANNEL_NOT_FOUND";
            case 400 -> "INVALID_PAYLOAD";
            default -> "INTERNAL_ERROR";
        };
    }
}
