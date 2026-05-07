package com.khalwsh.chat_service.controller;

import com.khalwsh.chat_service.dto.request.EditMessageRequest;
import com.khalwsh.chat_service.dto.request.SendMessageRequest;
import com.khalwsh.chat_service.dto.response.MessageResponse;
import com.khalwsh.chat_service.dto.response.WebSocketEvent;
import com.khalwsh.chat_service.service.ChannelService;
import com.khalwsh.chat_service.service.MessageService;
import com.khalwsh.chat_service.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ChannelService channelService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/channels/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable String id,
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        MessageResponse response = messageService.sendMessage(id, request, user.getUserId(), user.getRole());
        broadcastMessageEvent(WebSocketEvent.NEW_MESSAGE, response);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/channels/{id}/messages")
    public ResponseEntity<?> getMessages(
            @PathVariable String id,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        if (!channelService.isMember(id, user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of this channel");
        }

        if (before != null) {
            return ResponseEntity.ok(messageService.getChannelMessagesBefore(id, before, limit));
        }
        if (after != null) {
            return ResponseEntity.ok(messageService.getChannelMessagesAfter(id, after, limit));
        }
        return ResponseEntity.ok(messageService.getChannelMessages(id, page, limit));
    }

    @PutMapping("/messages/{id}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable String id,
            @Valid @RequestBody EditMessageRequest request,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        MessageResponse response = messageService.editMessage(id, request.getContent(), user.getUserId(), user.getRole());
        broadcastMessageEvent(WebSocketEvent.EDIT_MESSAGE, response);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        MessageResponse deleted = messageService.deleteMessage(id, user.getUserId(), user.getRole());

        // null means already-deleted — skip the broadcast
        if (deleted != null) {
            broadcastDeleteEvent(deleted, id);
        }

        return ResponseEntity.ok().build();
    }

    // thread messages broadcast to both topics; clients dedupe on payload.id
    private void broadcastMessageEvent(String action, MessageResponse message) {
        WebSocketEvent<MessageResponse> event = WebSocketEvent.of(action, message);
        messagingTemplate.convertAndSend("/topic/channel/" + message.getChannelId(), event);
        if (message.getThreadId() != null) {
            messagingTemplate.convertAndSend("/topic/thread/" + message.getThreadId(), event);
        }
    }

    private void broadcastDeleteEvent(MessageResponse deleted, String messageId) {
        WebSocketEvent<Map<String, String>> event = WebSocketEvent.of(
                WebSocketEvent.DELETE_MESSAGE, Map.of("messageId", messageId));
        messagingTemplate.convertAndSend("/topic/channel/" + deleted.getChannelId(), event);
        if (deleted.getThreadId() != null) {
            messagingTemplate.convertAndSend("/topic/thread/" + deleted.getThreadId(), event);
        }
    }
}
