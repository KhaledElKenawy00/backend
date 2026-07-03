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

import com.virtualoffice.workspace.dto.request.UpdateDeskRequest;
import com.virtualoffice.workspace.dto.request.UpdateStatusRequest;
import com.virtualoffice.workspace.dto.response.DeskResponse;
import com.virtualoffice.workspace.dto.response.PageResponse;
import com.virtualoffice.workspace.service.DeskService;
import com.virtualoffice.workspace.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Desks", description = "Workspace membership, per-workspace profile, presence, and status")
@RestController
@RequestMapping("/api/workspace/{workspaceId}/desks")
public class DeskController {

    private final DeskService deskService;

    public DeskController(DeskService deskService) {
        this.deskService = deskService;
    }

    @Operation(summary = "List members (paginated)", description = "Requires MEMBER. Pages are 0-based.")
    @GetMapping
    public PageResponse<DeskResponse> members(@PathVariable Long workspaceId,
                                              @PageableDefault(size = 20) Pageable pageable,
                                              HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return PageResponse.of(deskService.getMembers(workspaceId, userId, pageable));
    }

    @Operation(summary = "Get my desk", description = "The caller's own desk with links and widgets.")
    @GetMapping("/me")
    public DeskResponse myDesk(@PathVariable Long workspaceId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return deskService.getMyDesk(workspaceId, userId);
    }

    @Operation(summary = "Get a desk by id", description = "Requires MEMBER.")
    @GetMapping("/{deskId}")
    public DeskResponse get(@PathVariable Long workspaceId, @PathVariable Long deskId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return deskService.getDesk(workspaceId, deskId, userId);
    }

    @Operation(summary = "Update my desk",
            description = "Owner-only (even admins can't edit another user's desk). links/widgets replace wholesale.")
    @PutMapping("/{deskId}")
    public DeskResponse update(@PathVariable Long workspaceId,
                               @PathVariable Long deskId,
                               @Valid @RequestBody UpdateDeskRequest request,
                               HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return deskService.updateDesk(workspaceId, deskId, request, userId);
    }

    @Operation(summary = "Update my status", description = "Owner-only. Sets presence status shown to others.")
    @PatchMapping("/{deskId}/status")
    public DeskResponse updateStatus(@PathVariable Long workspaceId,
                                     @PathVariable Long deskId,
                                     @Valid @RequestBody UpdateStatusRequest request,
                                     HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return deskService.updateStatus(workspaceId, deskId, request, userId);
    }

    @Operation(summary = "Remove a member", description = "Requires ADMIN. Deactivates the desk. 409 for the OWNER desk.")
    @DeleteMapping("/{deskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long workspaceId, @PathVariable Long deskId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        deskService.removeMember(workspaceId, deskId, userId);
    }
}
