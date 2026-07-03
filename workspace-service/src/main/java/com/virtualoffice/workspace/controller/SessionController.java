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
package com.virtualoffice.workspace.controller;

import com.virtualoffice.workspace.dto.request.PresenceBatchRequest;
import com.virtualoffice.workspace.dto.request.PresenceSyncRequest;
import com.virtualoffice.workspace.dto.response.ChatContextResponse;
import com.virtualoffice.workspace.dto.response.JoinValidationResponse;
import com.virtualoffice.workspace.dto.response.MemberRoleResponse;
import com.virtualoffice.workspace.dto.response.SessionConfigResponse;
import com.virtualoffice.workspace.dto.response.ZoneResponse;
import com.virtualoffice.workspace.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Server-to-server API consumed by the forked Colyseus server, room-service, and chat-service.
 * Under {@code /api/internal/**} — blocked at the gateway and guarded by InternalAuthFilter
 * (X-Internal-Token); there is no end-user identity here.
 */
@Tag(name = "Session (internal)",
        description = "Server-to-server API for the Colyseus fork, room-service, and chat-service. "
                + "Requires the X-Internal-Token header; blocked at the gateway for external clients.")
@RestController
@RequestMapping("/api/internal/workspace/{workspaceId}")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "Room boot config", description = "Workspace meta + layout + active desks + active map objects.")
    @GetMapping("/session-config")
    public SessionConfigResponse sessionConfig(@PathVariable Long workspaceId) {
        return sessionService.getSessionConfig(workspaceId);
    }

    @Operation(summary = "Validate a join (Colyseus onAuth)",
            description = "Returns identity, spawn, avatar, and role. 404 if the user has no active desk.")
    @GetMapping("/join-validation/{userId}")
    public JoinValidationResponse validateJoin(@PathVariable Long workspaceId, @PathVariable Long userId) {
        return sessionService.validateJoin(workspaceId, userId);
    }

    @Operation(summary = "Get a member's workspace role",
            description = "Workspace-scoped role check for chat-service / room-service. 404 if no active desk.")
    @GetMapping("/members/{userId}/role")
    public MemberRoleResponse memberRole(@PathVariable Long workspaceId, @PathVariable Long userId) {
        return sessionService.getMemberRole(workspaceId, userId);
    }

    @Operation(summary = "Sync one presence update", description = "Writes isOnline/lastSeenAt and any non-null status/position.")
    @PostMapping("/presence")
    public void presence(@PathVariable Long workspaceId, @Valid @RequestBody PresenceSyncRequest request) {
        sessionService.syncPresence(workspaceId, request);
    }

    @Operation(summary = "Sync a batch of presence updates", description = "Colyseus timer flush; missing desks are skipped.")
    @PostMapping("/presence/batch")
    public void presenceBatch(@PathVariable Long workspaceId, @Valid @RequestBody PresenceBatchRequest request) {
        sessionService.syncPresenceBatch(workspaceId, request);
    }

    @Operation(summary = "List zones", description = "Zone bounds + voiceRoomId + proximityRadius for room-service voice.")
    @GetMapping("/zones")
    public List<ZoneResponse> zones(@PathVariable Long workspaceId) {
        return sessionService.getZones(workspaceId);
    }

    @Operation(summary = "Get the chat context", description = "Canonical workspace channel key (workspace:{id}) for chat-service.")
    @GetMapping("/chat-context")
    public ChatContextResponse chatContext(@PathVariable Long workspaceId) {
        return sessionService.getChatContext(workspaceId);
    }
}
