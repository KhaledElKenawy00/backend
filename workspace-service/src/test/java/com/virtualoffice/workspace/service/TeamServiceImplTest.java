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

import com.virtualoffice.workspace.dto.mapper.TeamMapperImpl;
import com.virtualoffice.workspace.dto.request.CreateTeamRequest;
import com.virtualoffice.workspace.dto.request.UpdateTeamRequest;
import com.virtualoffice.workspace.dto.response.TeamResponse;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.Team;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.TeamRepository;
import com.virtualoffice.workspace.service.impl.TeamServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceImplTest {

    private TeamRepository teamRepository;
    private WorkspaceAccessGuard accessGuard;
    private TeamServiceImpl service;

    @BeforeEach
    void setUp() {
        teamRepository = mock(TeamRepository.class);
        accessGuard = mock(WorkspaceAccessGuard.class);
        service = new TeamServiceImpl(teamRepository, accessGuard, new TeamMapperImpl());
    }

    @Test
    void createRequiresAdminAndUniqueName() {
        when(teamRepository.existsByWorkspaceIdAndName(1L, "Eng")).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });

        TeamResponse r = service.createTeam(1L, new CreateTeamRequest("Eng", "Engineering"), 5L);

        assertThat(r.id()).isEqualTo(10L);
        assertThat(r.name()).isEqualTo("Eng");
        verify(accessGuard).requireRole(1L, 5L, WorkspaceRole.ADMIN);
    }

    @Test
    void createRejectsDuplicateName() {
        when(teamRepository.existsByWorkspaceIdAndName(1L, "Eng")).thenReturn(true);
        assertThatThrownBy(() -> service.createTeam(1L, new CreateTeamRequest("Eng", null), 5L))
                .isInstanceOf(ConflictException.class);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateRenamesToUniqueName() {
        Team team = Team.builder().id(3L).workspaceId(1L).name("Eng").build();
        when(teamRepository.findByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(team));
        when(teamRepository.existsByWorkspaceIdAndName(1L, "Platform")).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(i -> i.getArgument(0));

        TeamResponse r = service.updateTeam(1L, 3L, new UpdateTeamRequest("Platform", "desc"), 5L);
        assertThat(r.name()).isEqualTo("Platform");
    }

    @Test
    void updateToDuplicateNameIsConflict() {
        Team team = Team.builder().id(3L).workspaceId(1L).name("Eng").build();
        when(teamRepository.findByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(team));
        when(teamRepository.existsByWorkspaceIdAndName(1L, "Ops")).thenReturn(true);
        assertThatThrownBy(() -> service.updateTeam(1L, 3L, new UpdateTeamRequest("Ops", null), 5L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateWithSameNameSkipsUniquenessCheck() {
        Team team = Team.builder().id(3L).workspaceId(1L).name("Eng").build();
        when(teamRepository.findByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenAnswer(i -> i.getArgument(0));

        // name unchanged + description only -> no existsBy lookup, no conflict
        TeamResponse r = service.updateTeam(1L, 3L, new UpdateTeamRequest("Eng", "new desc"), 5L);
        assertThat(r.description()).isEqualTo("new desc");
        org.mockito.Mockito.verify(teamRepository, never()).existsByWorkspaceIdAndName(1L, "Eng");
    }

    @Test
    void getTeamsRequiresMembership() {
        when(teamRepository.findByWorkspaceId(1L)).thenReturn(java.util.List.of());
        assertThat(service.getTeams(1L, 5L)).isEmpty();
        org.mockito.Mockito.verify(accessGuard).requireMember(1L, 5L);
    }

    @Test
    void updateMissingTeamThrowsNotFound() {
        when(teamRepository.findByIdAndWorkspaceId(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateTeam(1L, 99L, new UpdateTeamRequest("X", null), 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteMissingTeamThrowsNotFound() {
        when(teamRepository.findByIdAndWorkspaceId(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteTeam(1L, 99L, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesTeam() {
        Team team = Team.builder().id(3L).workspaceId(1L).name("Eng").build();
        when(teamRepository.findByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(team));
        service.deleteTeam(1L, 3L, 5L);
        verify(teamRepository).delete(team);
        verify(accessGuard).requireRole(1L, 5L, WorkspaceRole.ADMIN);
    }
}
