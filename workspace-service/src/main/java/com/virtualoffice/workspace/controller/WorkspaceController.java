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

import com.virtualoffice.workspace.dto.request.CreateWorkspaceRequest;
import com.virtualoffice.workspace.dto.request.UpdateWorkspaceRequest;
import com.virtualoffice.workspace.dto.response.WorkspaceResponse;
import com.virtualoffice.workspace.service.WorkspaceService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspaceRequest request,
                                                    HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workspaceService.createWorkspace(request, userId));
    }

    @GetMapping("/mine")
    public List<WorkspaceResponse> mine(HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return workspaceService.getMyWorkspaces(userId);
    }

    @GetMapping("/{id}")
    public WorkspaceResponse get(@PathVariable Long id, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return workspaceService.getWorkspace(id, userId);
    }

    @PutMapping("/{id}")
    public WorkspaceResponse update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateWorkspaceRequest request,
                                    HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return workspaceService.updateWorkspace(id, request, userId);
    }

    @DeleteMapping("/{id}")
    public WorkspaceResponse archive(@PathVariable Long id, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return workspaceService.archiveWorkspace(id, userId);
    }

    @PostMapping("/{id}/rotate-invite-token")
    public WorkspaceResponse rotateInviteToken(@PathVariable Long id, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return workspaceService.rotateInviteToken(id, userId);
    }
}
