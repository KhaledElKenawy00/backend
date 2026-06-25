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
package com.virtualoffice.workspace.service.impl;

import com.virtualoffice.workspace.dto.mapper.DeskMapper;
import com.virtualoffice.workspace.dto.mapper.MapObjectMapper;
import com.virtualoffice.workspace.dto.request.PresenceBatchRequest;
import com.virtualoffice.workspace.dto.request.PresenceSyncRequest;
import com.virtualoffice.workspace.dto.response.ChatContextResponse;
import com.virtualoffice.workspace.dto.response.DeskResponse;
import com.virtualoffice.workspace.dto.response.JoinValidationResponse;
import com.virtualoffice.workspace.dto.response.MemberRoleResponse;
import com.virtualoffice.workspace.dto.response.SessionConfigResponse;
import com.virtualoffice.workspace.dto.response.ZoneResponse;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.Workspace;
import com.virtualoffice.workspace.repository.DeskRepository;
import com.virtualoffice.workspace.repository.MapObjectRepository;
import com.virtualoffice.workspace.repository.WorkspaceRepository;
import com.virtualoffice.workspace.repository.ZoneRepository;
import com.virtualoffice.workspace.service.LayoutService;
import com.virtualoffice.workspace.service.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class SessionServiceImpl implements SessionService {

    private final WorkspaceRepository workspaceRepository;
    private final DeskRepository deskRepository;
    private final MapObjectRepository mapObjectRepository;
    private final ZoneRepository zoneRepository;
    private final LayoutService layoutService;
    private final DeskMapper deskMapper;
    private final MapObjectMapper mapObjectMapper;

    public SessionServiceImpl(WorkspaceRepository workspaceRepository,
                              DeskRepository deskRepository,
                              MapObjectRepository mapObjectRepository,
                              ZoneRepository zoneRepository,
                              LayoutService layoutService,
                              DeskMapper deskMapper,
                              MapObjectMapper mapObjectMapper) {
        this.workspaceRepository = workspaceRepository;
        this.deskRepository = deskRepository;
        this.mapObjectRepository = mapObjectRepository;
        this.zoneRepository = zoneRepository;
        this.layoutService = layoutService;
        this.deskMapper = deskMapper;
        this.mapObjectMapper = mapObjectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public SessionConfigResponse getSessionConfig(Long workspaceId) {
        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("workspace not found: " + workspaceId));

        List<DeskResponse> desks = deskRepository.findByWorkspaceIdAndIsActiveTrue(workspaceId).stream()
                .map(d -> deskMapper.toResponse(d, List.of(), List.of()))
                .toList();
        var mapObjects = mapObjectMapper.toResponseList(
                mapObjectRepository.findByWorkspaceIdAndIsActiveTrue(workspaceId));

        return new SessionConfigResponse(ws.getId(), ws.getName(), ws.getStatus(), ws.getInviteToken(),
                layoutService.getLayoutInternal(workspaceId), desks, mapObjects);
    }

    @Override
    @Transactional(readOnly = true)
    public JoinValidationResponse validateJoin(Long workspaceId, Long userId) {
        Desk desk = activeDeskOrThrow(workspaceId, userId);
        return new JoinValidationResponse(userId, workspaceId, desk.getId(), desk.getFullName(),
                desk.getAvatarCharacter(), desk.getPositionX(), desk.getPositionY(), desk.getRole(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberRoleResponse getMemberRole(Long workspaceId, Long userId) {
        Desk desk = activeDeskOrThrow(workspaceId, userId);
        return new MemberRoleResponse(userId, workspaceId, desk.getRole(), desk.isActive());
    }

    @Override
    public void syncPresence(Long workspaceId, PresenceSyncRequest request) {
        applyPresence(workspaceId, request);
    }

    @Override
    public void syncPresenceBatch(Long workspaceId, PresenceBatchRequest request) {
        // best-effort: a desk that no longer exists is skipped rather than failing the whole batch
        for (PresenceSyncRequest update : request.updates()) {
            deskRepository.findByWorkspaceIdAndUserId(workspaceId, update.userId())
                    .ifPresent(desk -> writePresence(desk, update));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponse> getZones(Long workspaceId) {
        return zoneRepository.findByWorkspaceId(workspaceId).stream()
                .map(z -> new ZoneResponse(z.getId(), z.getType(), z.getName(), z.getX(), z.getY(),
                        z.getWidth(), z.getHeight(), z.getVoiceRoomId(), z.getProximityRadius()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatContextResponse getChatContext(Long workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new ResourceNotFoundException("workspace not found: " + workspaceId);
        }
        return new ChatContextResponse(workspaceId, "workspace:" + workspaceId);
    }

    // --- helpers ---

    private void applyPresence(Long workspaceId, PresenceSyncRequest request) {
        Desk desk = deskRepository.findByWorkspaceIdAndUserId(workspaceId, request.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "no desk for user " + request.userId() + " in workspace " + workspaceId));
        writePresence(desk, request);
    }

    private void writePresence(Desk desk, PresenceSyncRequest request) {
        desk.setOnline(request.isOnline());
        desk.setLastSeenAt(Instant.now());
        if (request.status() != null) desk.setStatus(request.status());
        if (request.statusEmoji() != null) desk.setStatusEmoji(request.statusEmoji());
        if (request.positionX() != null) desk.setPositionX(request.positionX());
        if (request.positionY() != null) desk.setPositionY(request.positionY());
        deskRepository.save(desk);
    }

    private Desk activeDeskOrThrow(Long workspaceId, Long userId) {
        return deskRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(Desk::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "no active desk for user " + userId + " in workspace " + workspaceId));
    }
}
