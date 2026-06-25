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

import com.virtualoffice.workspace.dto.LayoutLayer;
import com.virtualoffice.workspace.dto.LayoutSpawnPoint;
import com.virtualoffice.workspace.dto.LayoutTileset;
import com.virtualoffice.workspace.dto.LayoutZone;
import com.virtualoffice.workspace.dto.request.UpdateLayoutRequest;
import com.virtualoffice.workspace.dto.response.LayoutResponse;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.MapLayer;
import com.virtualoffice.workspace.model.SpawnPoint;
import com.virtualoffice.workspace.model.Tileset;
import com.virtualoffice.workspace.model.Workspace;
import com.virtualoffice.workspace.model.Zone;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.MapLayerRepository;
import com.virtualoffice.workspace.repository.SpawnPointRepository;
import com.virtualoffice.workspace.repository.TilesetRepository;
import com.virtualoffice.workspace.repository.WorkspaceRepository;
import com.virtualoffice.workspace.repository.ZoneRepository;
import com.virtualoffice.workspace.service.LayoutService;
import com.virtualoffice.workspace.service.WorkspaceAccessGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class LayoutServiceImpl implements LayoutService {

    private final WorkspaceRepository workspaceRepository;
    private final TilesetRepository tilesetRepository;
    private final MapLayerRepository mapLayerRepository;
    private final ZoneRepository zoneRepository;
    private final SpawnPointRepository spawnPointRepository;
    private final WorkspaceAccessGuard accessGuard;

    @PersistenceContext
    private EntityManager entityManager;

    public LayoutServiceImpl(WorkspaceRepository workspaceRepository,
                             TilesetRepository tilesetRepository,
                             MapLayerRepository mapLayerRepository,
                             ZoneRepository zoneRepository,
                             SpawnPointRepository spawnPointRepository,
                             WorkspaceAccessGuard accessGuard) {
        this.workspaceRepository = workspaceRepository;
        this.tilesetRepository = tilesetRepository;
        this.mapLayerRepository = mapLayerRepository;
        this.zoneRepository = zoneRepository;
        this.spawnPointRepository = spawnPointRepository;
        this.accessGuard = accessGuard;
    }

    @Override
    @Transactional(readOnly = true)
    public LayoutResponse getLayout(Long workspaceId, Long requesterId) {
        accessGuard.requireMember(workspaceId, requesterId);
        return assemble(findOrThrow(workspaceId));
    }

    @Override
    @Transactional(readOnly = true)
    public LayoutResponse getLayoutInternal(Long workspaceId) {
        return assemble(findOrThrow(workspaceId));
    }

    @Override
    public LayoutResponse updateLayout(Long workspaceId, UpdateLayoutRequest request, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        Workspace ws = findOrThrow(workspaceId);

        if (!Objects.equals(ws.getLayoutVersion(), request.expectedVersion())) {
            throw new ConflictException("layout was modified concurrently; expected version "
                    + request.expectedVersion() + " but current is " + ws.getLayoutVersion());
        }

        List<LayoutTileset> tilesets = nullToEmpty(request.tilesets());
        List<LayoutLayer> layers = nullToEmpty(request.layers());
        List<LayoutZone> zones = nullToEmpty(request.zones());
        List<LayoutSpawnPoint> spawns = nullToEmpty(request.spawnPoints());
        validate(tilesets, layers, zones);

        ws.setTileSize(request.tileSize());
        ws.setMapWidth(request.mapWidth());
        ws.setMapHeight(request.mapHeight());
        ws.setMapGeometry(request.mapGeometry());
        // force a version bump even if the scalar map fields are unchanged
        entityManager.lock(ws, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

        // full replace of the normalized child rows
        tilesetRepository.deleteByWorkspaceId(workspaceId);
        mapLayerRepository.deleteByWorkspaceId(workspaceId);
        zoneRepository.deleteByWorkspaceId(workspaceId);
        spawnPointRepository.deleteByWorkspaceId(workspaceId);
        entityManager.flush();

        tilesets.forEach(t -> tilesetRepository.save(Tileset.builder()
                .workspaceId(workspaceId).name(t.name()).imageUrl(t.imageUrl()).firstGid(t.firstGid())
                .tileWidth(t.tileWidth()).tileHeight(t.tileHeight()).columns(t.columns()).tileCount(t.tileCount())
                .build()));
        layers.forEach(l -> mapLayerRepository.save(MapLayer.builder()
                .workspaceId(workspaceId).name(l.name()).layerIndex(l.layerIndex())
                .collides(l.collides()).data(l.data()).build()));
        zones.forEach(z -> zoneRepository.save(Zone.builder()
                .workspaceId(workspaceId).type(z.type()).name(z.name()).x(z.x()).y(z.y())
                .width(z.width()).height(z.height()).voiceRoomId(z.voiceRoomId())
                .proximityRadius(z.proximityRadius()).build()));
        spawns.forEach(s -> spawnPointRepository.save(SpawnPoint.builder()
                .workspaceId(workspaceId).x(s.x()).y(s.y()).label(s.label()).isDefault(s.isDefault())
                .build()));

        workspaceRepository.save(ws);
        return assemble(ws);
    }

    // --- helpers ---

    private void validate(List<LayoutTileset> tilesets, List<LayoutLayer> layers, List<LayoutZone> zones) {
        if (layers.stream().map(LayoutLayer::layerIndex).distinct().count() != layers.size()) {
            throw new IllegalArgumentException("layer indexes must be unique");
        }
        if (tilesets.stream().map(LayoutTileset::firstGid).distinct().count() != tilesets.size()) {
            throw new IllegalArgumentException("tileset firstGid values must be unique");
        }
        List<String> voiceRoomIds = zones.stream().map(LayoutZone::voiceRoomId).filter(Objects::nonNull).toList();
        if (voiceRoomIds.stream().distinct().count() != voiceRoomIds.size()) {
            throw new IllegalArgumentException("zone voiceRoomId values must be unique");
        }
    }

    private LayoutResponse assemble(Workspace ws) {
        List<LayoutTileset> tilesets = tilesetRepository.findByWorkspaceId(ws.getId()).stream()
                .map(t -> new LayoutTileset(t.getName(), t.getImageUrl(), t.getFirstGid(),
                        t.getTileWidth(), t.getTileHeight(), t.getColumns(), t.getTileCount()))
                .toList();
        List<LayoutLayer> layers = mapLayerRepository.findByWorkspaceIdOrderByLayerIndexAsc(ws.getId()).stream()
                .map(l -> new LayoutLayer(l.getName(), l.getLayerIndex(), l.isCollides(), l.getData()))
                .toList();
        List<LayoutZone> zones = zoneRepository.findByWorkspaceId(ws.getId()).stream()
                .map(z -> new LayoutZone(z.getType(), z.getName(), z.getX(), z.getY(),
                        z.getWidth(), z.getHeight(), z.getVoiceRoomId(), z.getProximityRadius()))
                .toList();
        List<LayoutSpawnPoint> spawns = spawnPointRepository.findByWorkspaceId(ws.getId()).stream()
                .map(s -> new LayoutSpawnPoint(s.getX(), s.getY(), s.getLabel(), s.isDefault()))
                .toList();
        return new LayoutResponse(ws.getId(), ws.getTileSize(), ws.getMapWidth(), ws.getMapHeight(),
                ws.getMapGeometry(), ws.getLayoutVersion(), tilesets, layers, zones, spawns);
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return Optional.ofNullable(list).orElse(List.of());
    }

    private Workspace findOrThrow(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("workspace not found: " + workspaceId));
    }
}
