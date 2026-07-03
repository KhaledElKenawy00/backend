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

import com.virtualoffice.workspace.dto.request.UpdateLayoutRequest;
import com.virtualoffice.workspace.dto.response.LayoutResponse;
import com.virtualoffice.workspace.service.LayoutService;
import com.virtualoffice.workspace.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Layout", description = "The 2D floorplan: tilesets, layers, zones, and spawn points")
@RestController
@RequestMapping("/api/workspace/{workspaceId}/layout")
public class LayoutController {

    private final LayoutService layoutService;

    public LayoutController(LayoutService layoutService) {
        this.layoutService = layoutService;
    }

    @Operation(summary = "Get the layout", description = "Requires MEMBER. The assembled floorplan the client renders from.")
    @GetMapping
    public LayoutResponse get(@PathVariable Long workspaceId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return layoutService.getLayout(workspaceId, userId);
    }

    @Operation(summary = "Update the layout",
            description = "Requires ADMIN. Replaces the floorplan atomically; send expectedVersion (409 on a stale version).")
    @PutMapping
    public LayoutResponse update(@PathVariable Long workspaceId,
                                 @Valid @RequestBody UpdateLayoutRequest request,
                                 HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return layoutService.updateLayout(workspaceId, request, userId);
    }
}
