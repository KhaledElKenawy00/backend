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

import com.virtualoffice.workspace.dto.mapper.MapObjectMapperImpl;
import com.virtualoffice.workspace.dto.request.CreateMapObjectRequest;
import com.virtualoffice.workspace.dto.request.UpdateMapObjectRequest;
import com.virtualoffice.workspace.dto.response.MapObjectResponse;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.MapObject;
import com.virtualoffice.workspace.model.enums.MapObjectType;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.MapObjectRepository;
import com.virtualoffice.workspace.service.impl.MapObjectServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapObjectServiceImplTest {

    private MapObjectRepository repository;
    private WorkspaceAccessGuard accessGuard;
    private MapObjectServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(MapObjectRepository.class);
        accessGuard = mock(WorkspaceAccessGuard.class);
        service = new MapObjectServiceImpl(repository, accessGuard, new MapObjectMapperImpl());
    }

    @Test
    void createGeneratesRoomIdAndRequiresAdmin() {
        when(repository.save(any(MapObject.class))).thenAnswer(i -> {
            MapObject m = i.getArgument(0);
            m.setId(1L);
            return m;
        });

        MapObjectResponse r = service.createMapObject(1L,
                new CreateMapObjectRequest(MapObjectType.WHITEBOARD, "WB-A", 10, 20, 4), 5L);

        assertThat(r.roomId()).isNotBlank();
        assertThat(r.isActive()).isTrue();
        assertThat(r.type()).isEqualTo(MapObjectType.WHITEBOARD);
        verify(accessGuard).requireRole(1L, 5L, WorkspaceRole.ADMIN);
    }

    @Test
    void toggleFlipsActive() {
        MapObject obj = MapObject.builder().id(2L).workspaceId(1L).isActive(true).build();
        when(repository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Optional.of(obj));
        when(repository.save(any(MapObject.class))).thenAnswer(i -> i.getArgument(0));

        MapObjectResponse r = service.toggleActive(1L, 2L, 5L);
        assertThat(r.isActive()).isFalse();
    }

    @Test
    void updateAppliesNonNullFields() {
        MapObject obj = MapObject.builder().id(2L).workspaceId(1L).label("old")
                .positionX(1).positionY(1).capacity(1).isActive(true).build();
        when(repository.findByIdAndWorkspaceId(2L, 1L)).thenReturn(Optional.of(obj));
        when(repository.save(any(MapObject.class))).thenAnswer(i -> i.getArgument(0));

        MapObjectResponse r = service.updateMapObject(1L, 2L,
                new UpdateMapObjectRequest("new", 9, null, 5), 5L);

        assertThat(r.label()).isEqualTo("new");
        assertThat(r.positionX()).isEqualTo(9);
        assertThat(r.positionY()).isEqualTo(1);   // untouched
        assertThat(r.capacity()).isEqualTo(5);
        verify(accessGuard).requireRole(1L, 5L, WorkspaceRole.ADMIN);
    }

    @Test
    void updateMissingObjectThrowsNotFound() {
        when(repository.findByIdAndWorkspaceId(9L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteMapObject(1L, 9L, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
