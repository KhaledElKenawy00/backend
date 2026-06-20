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
package com.virtualoffice.workspace.service;

import com.virtualoffice.workspace.dto.LayoutLayer;
import com.virtualoffice.workspace.dto.LayoutTileset;
import com.virtualoffice.workspace.dto.LayoutZone;
import com.virtualoffice.workspace.dto.request.UpdateLayoutRequest;
import com.virtualoffice.workspace.model.enums.ZoneType;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.model.Workspace;
import com.virtualoffice.workspace.repository.MapLayerRepository;
import com.virtualoffice.workspace.repository.SpawnPointRepository;
import com.virtualoffice.workspace.repository.TilesetRepository;
import com.virtualoffice.workspace.repository.WorkspaceRepository;
import com.virtualoffice.workspace.repository.ZoneRepository;
import com.virtualoffice.workspace.service.impl.LayoutServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LayoutServiceImplTest {

    private WorkspaceRepository workspaceRepository;
    private LayoutServiceImpl service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        service = new LayoutServiceImpl(workspaceRepository, mock(TilesetRepository.class),
                mock(MapLayerRepository.class), mock(ZoneRepository.class),
                mock(SpawnPointRepository.class), mock(WorkspaceAccessGuard.class));
    }

    private Workspace workspaceWithVersion(long version) {
        return Workspace.builder().id(1L).layoutVersion(version).build();
    }

    @Test
    void staleVersionIsConflict() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspaceWithVersion(3L)));
        UpdateLayoutRequest req = new UpdateLayoutRequest(1L, 32, 80, 60, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateLayout(1L, req, 5L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void duplicateLayerIndexIsRejected() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspaceWithVersion(0L)));
        UpdateLayoutRequest req = new UpdateLayoutRequest(0L, 32, 80, 60, null, null,
                List.of(new LayoutLayer("Ground", 0, false, "[]"),
                        new LayoutLayer("Walls", 0, true, "[]")),
                null, null);
        assertThatThrownBy(() -> service.updateLayout(1L, req, 5L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateTilesetFirstGidIsRejected() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspaceWithVersion(0L)));
        UpdateLayoutRequest req = new UpdateLayoutRequest(0L, 32, 80, 60, null,
                List.of(new LayoutTileset("a", "a.png", 1, 32, 32, 8, 64),
                        new LayoutTileset("b", "b.png", 1, 32, 32, 8, 64)),
                null, null, null);
        assertThatThrownBy(() -> service.updateLayout(1L, req, 5L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateZoneVoiceRoomIdIsRejected() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspaceWithVersion(0L)));
        UpdateLayoutRequest req = new UpdateLayoutRequest(0L, 32, 80, 60, null, null, null,
                List.of(new LayoutZone(ZoneType.MEETING_ROOM, "A", 0, 0, 1, 1, "v", null),
                        new LayoutZone(ZoneType.MEETING_ROOM, "B", 2, 2, 1, 1, "v", null)),
                null);
        assertThatThrownBy(() -> service.updateLayout(1L, req, 5L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
