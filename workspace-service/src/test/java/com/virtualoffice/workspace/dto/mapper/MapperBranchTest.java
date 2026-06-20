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
package com.virtualoffice.workspace.dto.mapper;

import com.virtualoffice.workspace.dto.response.DeskResponse;
import com.virtualoffice.workspace.dto.response.DeskWidgetResponse;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.DeskWidget;
import com.virtualoffice.workspace.model.MapObject;
import com.virtualoffice.workspace.model.Team;
import com.virtualoffice.workspace.model.Workspace;
import com.virtualoffice.workspace.model.WorkspaceInvitation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the generated MapStruct mappers' null-guard branches (null input -> null output)
 * and both sides of the optional-list / source-null guards, which e2e flows don't hit.
 */
class MapperBranchTest {

    private final WorkspaceMapper workspaceMapper = new WorkspaceMapperImpl();
    private final TeamMapper teamMapper = new TeamMapperImpl();
    private final MapObjectMapper mapObjectMapper = new MapObjectMapperImpl();
    private final InvitationMapper invitationMapper = new InvitationMapperImpl();
    private final DeskMapper deskMapper = new DeskMapperImpl();

    @Test
    void simpleMappersReturnNullForNullInput() {
        assertThat(workspaceMapper.toResponse(null)).isNull();
        assertThat(workspaceMapper.toResponseList(null)).isNull();
        assertThat(teamMapper.toResponse(null)).isNull();
        assertThat(teamMapper.toResponseList(null)).isNull();
        assertThat(mapObjectMapper.toResponse(null)).isNull();
        assertThat(mapObjectMapper.toResponseList(null)).isNull();
        assertThat(invitationMapper.toResponse(null)).isNull();
        assertThat(invitationMapper.toResponseList(null)).isNull();
    }

    @Test
    void simpleMappersMapNonNullInput() {
        assertThat(workspaceMapper.toResponse(Workspace.builder().id(1L).build()).id()).isEqualTo(1L);
        assertThat(workspaceMapper.toResponseList(List.of(Workspace.builder().id(1L).build()))).hasSize(1);
        assertThat(teamMapper.toResponse(Team.builder().id(2L).build()).id()).isEqualTo(2L);
        assertThat(teamMapper.toResponseList(List.of(Team.builder().id(2L).build()))).hasSize(1);
        assertThat(mapObjectMapper.toResponse(MapObject.builder().id(3L).isActive(true).build()).id()).isEqualTo(3L);
        assertThat(mapObjectMapper.toResponseList(List.of(MapObject.builder().id(3L).build()))).hasSize(1);
        assertThat(invitationMapper.toResponse(WorkspaceInvitation.builder().id(4L).build()).id()).isEqualTo(4L);
        assertThat(invitationMapper.toResponseList(List.of(WorkspaceInvitation.builder().id(4L).build()))).hasSize(1);
    }

    @Test
    void deskWidgetMapperNullGuards() {
        assertThat(deskMapper.toWidget(null)).isNull();
        assertThat(deskMapper.toWidgets(null)).isNull();
        assertThat(deskMapper.toWidget(DeskWidget.builder().id(1L).type("CLOCK").build()).type()).isEqualTo("CLOCK");
        assertThat(deskMapper.toWidgets(List.of(DeskWidget.builder().id(1L).build()))).hasSize(1);
    }

    @Test
    void deskMapperCoversAllSourceNullCombinations() {
        Desk desk = Desk.builder().id(10L).userId(5L).isOnline(true).isActive(true).build();
        List<String> links = List.of("https://x");
        List<DeskWidgetResponse> widgets = List.of(new DeskWidgetResponse(1L, "CLOCK", "c", 0, "{}"));

        // all sources null -> null
        assertThat(deskMapper.toResponse(null, null, null)).isNull();
        // desk null, links non-null (short-circuits the all-null guard at links)
        assertThat(deskMapper.toResponse(null, links, null)).isNotNull();
        // desk null, links null, widgets non-null
        assertThat(deskMapper.toResponse(null, null, widgets)).isNotNull();
        // desk non-null, lists null (covers the list != null false branches)
        DeskResponse noLists = deskMapper.toResponse(desk, null, null);
        assertThat(noLists.links()).isNull();
        assertThat(noLists.widgets()).isNull();
        // everything present (covers desk != null + list != null true branches)
        DeskResponse full = deskMapper.toResponse(desk, links, widgets);
        assertThat(full.isOnline()).isTrue();
        assertThat(full.links()).containsExactly("https://x");
        assertThat(full.widgets()).hasSize(1);
    }
}
