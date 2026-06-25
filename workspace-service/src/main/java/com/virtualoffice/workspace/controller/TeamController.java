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
package com.virtualoffice.workspace.controller;

import com.virtualoffice.workspace.dto.request.CreateTeamRequest;
import com.virtualoffice.workspace.dto.request.UpdateTeamRequest;
import com.virtualoffice.workspace.dto.response.TeamResponse;
import com.virtualoffice.workspace.service.TeamService;
import com.virtualoffice.workspace.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspace/{workspaceId}/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@PathVariable Long workspaceId,
                                               @Valid @RequestBody CreateTeamRequest request,
                                               HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamService.createTeam(workspaceId, request, userId));
    }

    @GetMapping
    public List<TeamResponse> list(@PathVariable Long workspaceId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return teamService.getTeams(workspaceId, userId);
    }

    @PutMapping("/{teamId}")
    public TeamResponse update(@PathVariable Long workspaceId,
                               @PathVariable Long teamId,
                               @Valid @RequestBody UpdateTeamRequest request,
                               HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return teamService.updateTeam(workspaceId, teamId, request, userId);
    }

    @DeleteMapping("/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId,
                       @PathVariable Long teamId,
                       HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        teamService.deleteTeam(workspaceId, teamId, userId);
    }
}
