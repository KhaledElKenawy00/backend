package com.khalwsh.chat_service.controller;

import com.khalwsh.chat_service.dto.request.CreateChannelRequest;
import com.khalwsh.chat_service.dto.request.CreateDmRequest;
import com.khalwsh.chat_service.dto.response.ChannelResponse;
import com.khalwsh.chat_service.dto.response.PaginatedResponse;
import com.khalwsh.chat_service.service.ChannelService;
import com.khalwsh.chat_service.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping("/channels")
    public ResponseEntity<ChannelResponse> createChannel(
            @Valid @RequestBody CreateChannelRequest request,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        ChannelResponse response;
        try {
            response = channelService.createGroupChannel(request, user.getUserId());
        }catch(DuplicateKeyException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/channels")
    public ResponseEntity<PaginatedResponse<ChannelResponse>> getChannels(
            @RequestParam Integer workspaceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        PaginatedResponse<ChannelResponse> response = channelService.getWorkspaceChannels(workspaceId, user.getUserId(), page, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/channels/{id}")
    public ResponseEntity<ChannelResponse> getChannel(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        if (!channelService.isMember(id, user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of this channel");
        }
        return ResponseEntity.ok(channelService.getChannel(id));
    }

    @PostMapping("/channels/{id}/join")
    public ResponseEntity<Void> joinChannel(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        channelService.joinChannel(id, user.getUserId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/channels/{id}/leave")
    public ResponseEntity<Void> leaveChannel(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        channelService.leaveChannel(id, user.getUserId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dm")
    public ResponseEntity<ChannelResponse> getOrCreateDm(
            @Valid @RequestBody CreateDmRequest request,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        ChannelResponse response = channelService.getOrCreateDm(user.getUserId(), request.getTargetUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dm")
    public ResponseEntity<PaginatedResponse<ChannelResponse>> getDirectMessages(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);

        PaginatedResponse<ChannelResponse> response = channelService.getDirectMessages(user.getUserId(), page, limit);
        return ResponseEntity.ok(response);
    }
}
