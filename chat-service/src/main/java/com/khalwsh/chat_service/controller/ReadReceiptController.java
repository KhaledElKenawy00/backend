package com.khalwsh.chat_service.controller;

import com.khalwsh.chat_service.dto.request.MarkReadRequest;
import com.khalwsh.chat_service.dto.response.UnreadCountResponse;
import com.khalwsh.chat_service.service.ChannelService;
import com.khalwsh.chat_service.service.ReadReceiptService;
import com.khalwsh.chat_service.service.ThreadService;
import com.khalwsh.chat_service.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ReadReceiptController {

    private final ReadReceiptService readReceiptService;
    private final ChannelService channelService;
    private final ThreadService threadService;

    @PostMapping("/channels/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String id,
            @Valid @RequestBody MarkReadRequest request,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        if (!channelService.isMember(id, user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of this channel");
        }

        readReceiptService.markAsRead(id, user.getUserId(), request.getLastReadMessageId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/channels/{id}/unread")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        if (!channelService.isMember(id, user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of this channel");
        }

        UnreadCountResponse response = readReceiptService.getUnreadCount(id, user.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/threads/{threadId}/read")
    public ResponseEntity<Void> markThreadAsRead(
            @PathVariable String threadId,
            @Valid @RequestBody MarkReadRequest request,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        String channelId = threadService.getThread(threadId).getChannelId();
        if (!channelService.isMember(channelId, user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of the parent channel");
        }

        readReceiptService.markThreadAsRead(threadId, user.getUserId(), request.getLastReadMessageId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/threads/{threadId}/unread")
    public ResponseEntity<UnreadCountResponse> getThreadUnreadCount(
            @PathVariable String threadId,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        String channelId = threadService.getThread(threadId).getChannelId();
        if (!channelService.isMember(channelId, user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of the parent channel");
        }

        return ResponseEntity.ok(readReceiptService.getThreadUnreadCount(threadId, user.getUserId()));
    }
}
