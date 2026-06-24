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
import com.virtualoffice.workspace.dto.request.UpdateDeskRequest;
import com.virtualoffice.workspace.dto.request.UpdateStatusRequest;
import com.virtualoffice.workspace.messaging.WorkspaceChannelEventPublisher;
import com.virtualoffice.workspace.dto.response.DeskResponse;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.ForbiddenException;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.DeskLink;
import com.virtualoffice.workspace.model.DeskWidget;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.DeskLinkRepository;
import com.virtualoffice.workspace.repository.DeskRepository;
import com.virtualoffice.workspace.repository.DeskWidgetRepository;
import com.virtualoffice.workspace.service.DeskService;
import com.virtualoffice.workspace.service.WorkspaceAccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DeskServiceImpl implements DeskService {

    private final DeskRepository deskRepository;
    private final DeskLinkRepository deskLinkRepository;
    private final DeskWidgetRepository deskWidgetRepository;
    private final WorkspaceAccessGuard accessGuard;
    private final DeskMapper mapper;
    private final WorkspaceChannelEventPublisher channelEvents;

    public DeskServiceImpl(DeskRepository deskRepository,
                           DeskLinkRepository deskLinkRepository,
                           DeskWidgetRepository deskWidgetRepository,
                           WorkspaceAccessGuard accessGuard,
                           DeskMapper mapper,
                           WorkspaceChannelEventPublisher channelEvents) {
        this.deskRepository = deskRepository;
        this.deskLinkRepository = deskLinkRepository;
        this.deskWidgetRepository = deskWidgetRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
        this.channelEvents = channelEvents;
    }

    @Override
    @Transactional(readOnly = true)
    public DeskResponse getMyDesk(Long workspaceId, Long userId) {
        return full(accessGuard.requireMember(workspaceId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public DeskResponse getDesk(Long workspaceId, Long deskId, Long requesterId) {
        accessGuard.requireMember(workspaceId, requesterId);
        return full(findOrThrow(workspaceId, deskId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeskResponse> getMembers(Long workspaceId, Long requesterId, Pageable pageable) {
        accessGuard.requireMember(workspaceId, requesterId);
        // directory rows are lightweight — links/widgets are fetched only for single-desk reads
        return deskRepository.findByWorkspaceIdAndIsActiveTrue(workspaceId, pageable).map(this::brief);
    }

    @Override
    public DeskResponse updateDesk(Long workspaceId, Long deskId, UpdateDeskRequest request, Long requesterId) {
        Desk desk = requireOwnDesk(workspaceId, deskId, requesterId);

        if (request.fullName() != null) desk.setFullName(request.fullName());
        if (request.nickName() != null) desk.setNickName(request.nickName());
        if (request.title() != null) desk.setTitle(request.title());
        if (request.bio() != null) desk.setBio(request.bio());
        if (request.avatarCharacter() != null) desk.setAvatarCharacter(request.avatarCharacter());
        if (request.timezone() != null) desk.setTimezone(request.timezone());
        if (request.teamId() != null) desk.setTeamId(request.teamId());
        deskRepository.save(desk);

        if (request.links() != null) replaceLinks(deskId, request.links());
        if (request.widgets() != null) replaceWidgets(deskId, request.widgets());

        return full(desk);
    }

    @Override
    public DeskResponse updateStatus(Long workspaceId, Long deskId, UpdateStatusRequest request, Long requesterId) {
        Desk desk = requireOwnDesk(workspaceId, deskId, requesterId);
        desk.setStatus(request.status());
        desk.setStatusEmoji(request.statusEmoji());
        desk.setStatusCustomText(request.statusCustomText());
        return full(deskRepository.save(desk));
    }

    @Override
    public void removeMember(Long workspaceId, Long deskId, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        Desk desk = findOrThrow(workspaceId, deskId);
        if (desk.getRole() == WorkspaceRole.OWNER) {
            throw new ConflictException("the workspace owner cannot be removed");
        }
        desk.setActive(false);
        deskRepository.save(desk);
        // Drop the removed member from the workspace chat channel (chat-service, post-commit).
        channelEvents.memberRemoved(workspaceId, desk.getUserId());
    }

    // --- helpers ---

    private Desk requireOwnDesk(Long workspaceId, Long deskId, Long requesterId) {
        Desk desk = findOrThrow(workspaceId, deskId);
        if (!desk.getUserId().equals(requesterId)) {
            throw new ForbiddenException("you can only modify your own desk");
        }
        return desk;
    }

    private Desk findOrThrow(Long workspaceId, Long deskId) {
        return deskRepository.findByIdAndWorkspaceId(deskId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("desk not found: " + deskId));
    }

    private void replaceLinks(Long deskId, List<String> urls) {
        deskLinkRepository.deleteByDeskId(deskId);
        urls.stream().distinct().forEach(url ->
                deskLinkRepository.save(DeskLink.builder().deskId(deskId).url(url).build()));
    }

    private void replaceWidgets(Long deskId, List<UpdateDeskRequest.WidgetInput> widgets) {
        deskWidgetRepository.deleteByDeskId(deskId);
        widgets.forEach(w -> deskWidgetRepository.save(DeskWidget.builder()
                .deskId(deskId).type(w.type()).label(w.label()).position(w.position()).config(w.config())
                .build()));
    }

    private DeskResponse full(Desk desk) {
        List<String> links = deskLinkRepository.findByDeskId(desk.getId()).stream()
                .map(DeskLink::getUrl).toList();
        var widgets = mapper.toWidgets(deskWidgetRepository.findByDeskIdOrderByPositionAsc(desk.getId()));
        return mapper.toResponse(desk, links, widgets);
    }

    private DeskResponse brief(Desk desk) {
        return mapper.toResponse(desk, List.of(), List.of());
    }
}
