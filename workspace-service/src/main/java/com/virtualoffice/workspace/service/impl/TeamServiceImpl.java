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

import com.virtualoffice.workspace.dto.mapper.TeamMapper;
import com.virtualoffice.workspace.dto.request.CreateTeamRequest;
import com.virtualoffice.workspace.dto.request.UpdateTeamRequest;
import com.virtualoffice.workspace.dto.response.TeamResponse;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.Team;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.TeamRepository;
import com.virtualoffice.workspace.service.TeamService;
import com.virtualoffice.workspace.service.WorkspaceAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final WorkspaceAccessGuard accessGuard;
    private final TeamMapper mapper;

    public TeamServiceImpl(TeamRepository teamRepository, WorkspaceAccessGuard accessGuard, TeamMapper mapper) {
        this.teamRepository = teamRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
    }

    @Override
    public TeamResponse createTeam(Long workspaceId, CreateTeamRequest request, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        if (teamRepository.existsByWorkspaceIdAndName(workspaceId, request.name())) {
            throw new ConflictException("team name already exists: " + request.name());
        }
        Team team = teamRepository.save(Team.builder()
                .workspaceId(workspaceId)
                .name(request.name())
                .description(request.description())
                .build());
        return mapper.toResponse(team);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamResponse> getTeams(Long workspaceId, Long requesterId) {
        accessGuard.requireMember(workspaceId, requesterId);
        return mapper.toResponseList(teamRepository.findByWorkspaceId(workspaceId));
    }

    @Override
    public TeamResponse updateTeam(Long workspaceId, Long teamId, UpdateTeamRequest request, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        Team team = findOrThrow(workspaceId, teamId);

        if (request.name() != null && !request.name().equals(team.getName())
                && teamRepository.existsByWorkspaceIdAndName(workspaceId, request.name())) {
            throw new ConflictException("team name already exists: " + request.name());
        }
        if (request.name() != null) team.setName(request.name());
        if (request.description() != null) team.setDescription(request.description());

        return mapper.toResponse(teamRepository.save(team));
    }

    @Override
    public void deleteTeam(Long workspaceId, Long teamId, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        Team team = findOrThrow(workspaceId, teamId);
        // desk.team_id is ON DELETE SET NULL, so members are not orphaned.
        teamRepository.delete(team);
    }

    private Team findOrThrow(Long workspaceId, Long teamId) {
        return teamRepository.findByIdAndWorkspaceId(teamId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("team not found: " + teamId));
    }
}
