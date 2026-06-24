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

import com.virtualoffice.workspace.dto.mapper.WorkspaceMapper;
import com.virtualoffice.workspace.dto.request.CreateWorkspaceRequest;
import com.virtualoffice.workspace.dto.request.UpdateWorkspaceRequest;
import com.virtualoffice.workspace.dto.response.WorkspaceResponse;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.ForbiddenException;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.messaging.WorkspaceChannelEventPublisher;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.Workspace;
import com.virtualoffice.workspace.model.enums.AvatarCharacter;
import com.virtualoffice.workspace.model.enums.DeskStatus;
import com.virtualoffice.workspace.model.enums.InviteStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.model.enums.WorkspaceStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceVisibility;
import com.virtualoffice.workspace.repository.DeskRepository;
import com.virtualoffice.workspace.repository.WorkspaceRepository;
import com.virtualoffice.workspace.service.WorkspaceAccessGuard;
import com.virtualoffice.workspace.service.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WorkspaceServiceImpl implements WorkspaceService {

    // Sensible defaults for a freshly created (empty) floorplan; admins edit via the layout API.
    private static final int DEFAULT_TILE_SIZE = 32;
    private static final int DEFAULT_MAP_WIDTH = 80;
    private static final int DEFAULT_MAP_HEIGHT = 60;

    private final WorkspaceRepository workspaceRepository;
    private final DeskRepository deskRepository;
    private final WorkspaceAccessGuard accessGuard;
    private final WorkspaceMapper mapper;
    private final WorkspaceChannelEventPublisher channelEvents;

    public WorkspaceServiceImpl(WorkspaceRepository workspaceRepository,
                                DeskRepository deskRepository,
                                WorkspaceAccessGuard accessGuard,
                                WorkspaceMapper mapper,
                                WorkspaceChannelEventPublisher channelEvents) {
        this.workspaceRepository = workspaceRepository;
        this.deskRepository = deskRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
        this.channelEvents = channelEvents;
    }

    @Override
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, Long ownerId) {
        if (workspaceRepository.existsBySlug(request.slug())) {
            throw new ConflictException("slug already in use: " + request.slug());
        }

        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(request.name())
                .slug(request.slug())
                .ownerId(ownerId)
                .description(request.description())
                .logoUrl(request.logoUrl())
                .status(WorkspaceStatus.ACTIVE)
                .visibility(WorkspaceVisibility.INVITE_ONLY)
                .inviteToken(UUID.randomUUID())
                .defaultTimezone(request.defaultTimezone())
                .tileSize(DEFAULT_TILE_SIZE)
                .mapWidth(DEFAULT_MAP_WIDTH)
                .mapHeight(DEFAULT_MAP_HEIGHT)
                .build());

        // The creator gets an active OWNER desk in the same transaction.
        deskRepository.save(Desk.builder()
                .workspaceId(workspace.getId())
                .userId(ownerId)
                .avatarCharacter(AvatarCharacter.ADAM)
                .status(DeskStatus.ACTIVE)
                .role(WorkspaceRole.OWNER)
                .inviteStatus(InviteStatus.ACCEPTED)
                .positionX(0)
                .positionY(0)
                .isOnline(false)
                .isActive(true)
                .timezone(request.defaultTimezone())
                .joinedAt(Instant.now())
                .build());

        // chat-service provisions the canonical workspace channel off this event (fired post-commit).
        channelEvents.channelCreated(workspace.getId(), workspace.getName(), ownerId);

        return mapper.toResponse(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(Long workspaceId, Long requesterId) {
        accessGuard.requireMember(workspaceId, requesterId);
        return mapper.toResponse(findOrThrow(workspaceId));
    }

    @Override
    public WorkspaceResponse updateWorkspace(Long workspaceId, UpdateWorkspaceRequest request, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        Workspace workspace = findOrThrow(workspaceId);

        if (request.name() != null) workspace.setName(request.name());
        if (request.description() != null) workspace.setDescription(request.description());
        if (request.logoUrl() != null) workspace.setLogoUrl(request.logoUrl());
        if (request.defaultTimezone() != null) workspace.setDefaultTimezone(request.defaultTimezone());

        return mapper.toResponse(workspaceRepository.save(workspace));
    }

    @Override
    public WorkspaceResponse archiveWorkspace(Long workspaceId, Long requesterId) {
        Workspace workspace = findOrThrow(workspaceId);
        // owner-only — stricter than ADMIN
        if (!workspace.getOwnerId().equals(requesterId)) {
            throw new ForbiddenException("only the workspace owner can archive it");
        }
        workspace.setStatus(WorkspaceStatus.ARCHIVED);
        return mapper.toResponse(workspaceRepository.save(workspace));
    }

    @Override
    public WorkspaceResponse rotateInviteToken(Long workspaceId, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        Workspace workspace = findOrThrow(workspaceId);
        workspace.setInviteToken(UUID.randomUUID());
        return mapper.toResponse(workspaceRepository.save(workspace));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getMyWorkspaces(Long requesterId) {
        List<Long> ids = deskRepository.findByUserIdAndIsActiveTrue(requesterId).stream()
                .map(Desk::getWorkspaceId)
                .toList();
        return mapper.toResponseList(workspaceRepository.findAllById(ids));
    }

    private Workspace findOrThrow(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("workspace not found: " + workspaceId));
    }
}
