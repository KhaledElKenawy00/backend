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

import com.virtualoffice.workspace.dto.mapper.MapObjectMapper;
import com.virtualoffice.workspace.dto.request.CreateMapObjectRequest;
import com.virtualoffice.workspace.dto.request.UpdateMapObjectRequest;
import com.virtualoffice.workspace.dto.response.MapObjectResponse;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.MapObject;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.MapObjectRepository;
import com.virtualoffice.workspace.service.MapObjectService;
import com.virtualoffice.workspace.service.WorkspaceAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MapObjectServiceImpl implements MapObjectService {

    private final MapObjectRepository mapObjectRepository;
    private final WorkspaceAccessGuard accessGuard;
    private final MapObjectMapper mapper;

    public MapObjectServiceImpl(MapObjectRepository mapObjectRepository,
                                WorkspaceAccessGuard accessGuard,
                                MapObjectMapper mapper) {
        this.mapObjectRepository = mapObjectRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
    }

    @Override
    public MapObjectResponse createMapObject(Long workspaceId, CreateMapObjectRequest request, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        MapObject saved = mapObjectRepository.save(MapObject.builder()
                .workspaceId(workspaceId)
                .type(request.type())
                .label(request.label())
                .positionX(request.positionX())
                .positionY(request.positionY())
                .capacity(request.capacity())
                // stable Colyseus room id so in-progress screen-share / whiteboard sessions can rejoin
                .roomId(UUID.randomUUID().toString())
                .isActive(true)
                .build());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MapObjectResponse> getMapObjects(Long workspaceId, Long requesterId) {
        accessGuard.requireMember(workspaceId, requesterId);
        return mapper.toResponseList(mapObjectRepository.findByWorkspaceIdAndIsActiveTrue(workspaceId));
    }

    @Override
    public MapObjectResponse updateMapObject(Long workspaceId, Long id, UpdateMapObjectRequest request, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        MapObject obj = findOrThrow(workspaceId, id);
        if (request.label() != null) obj.setLabel(request.label());
        if (request.positionX() != null) obj.setPositionX(request.positionX());
        if (request.positionY() != null) obj.setPositionY(request.positionY());
        if (request.capacity() != null) obj.setCapacity(request.capacity());
        return mapper.toResponse(mapObjectRepository.save(obj));
    }

    @Override
    public MapObjectResponse toggleActive(Long workspaceId, Long id, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        MapObject obj = findOrThrow(workspaceId, id);
        obj.setActive(!obj.isActive());
        return mapper.toResponse(mapObjectRepository.save(obj));
    }

    @Override
    public void deleteMapObject(Long workspaceId, Long id, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        mapObjectRepository.delete(findOrThrow(workspaceId, id));
    }

    private MapObject findOrThrow(Long workspaceId, Long id) {
        return mapObjectRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("map object not found: " + id));
    }
}
