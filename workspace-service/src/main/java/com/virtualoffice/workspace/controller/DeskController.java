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

@RestController
@RequestMapping("/api/workspace/{workspaceId}/desks")
public class DeskController {

    private final DeskService deskService;

    public DeskController(DeskService deskService) {
        this.deskService = deskService;
    }

    @GetMapping
    public PageResponse<DeskResponse> members(@PathVariable Long workspaceId,
                                              @PageableDefault(size = 20) Pageable pageable,
                                              HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return PageResponse.of(deskService.getMembers(workspaceId, userId, pageable));
    }

    @GetMapping("/me")
    public DeskResponse myDesk(@PathVariable Long workspaceId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return deskService.getMyDesk(workspaceId, userId);
    }

    @GetMapping("/{deskId}")
    public DeskResponse get(@PathVariable Long workspaceId, @PathVariable Long deskId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return deskService.getDesk(workspaceId, deskId, userId);
    }

    @PutMapping("/{deskId}")
    public DeskResponse update(@PathVariable Long workspaceId,
                               @PathVariable Long deskId,
                               @Valid @RequestBody UpdateDeskRequest request,
                               HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return deskService.updateDesk(workspaceId, deskId, request, userId);
    }

    @PatchMapping("/{deskId}/status")
    public DeskResponse updateStatus(@PathVariable Long workspaceId,
                                     @PathVariable Long deskId,
                                     @Valid @RequestBody UpdateStatusRequest request,
                                     HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return deskService.updateStatus(workspaceId, deskId, request, userId);
    }

    @DeleteMapping("/{deskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long workspaceId, @PathVariable Long deskId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        deskService.removeMember(workspaceId, deskId, userId);
    }
}
