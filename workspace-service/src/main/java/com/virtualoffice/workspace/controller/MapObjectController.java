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

import com.virtualoffice.workspace.dto.request.CreateMapObjectRequest;
import com.virtualoffice.workspace.dto.request.UpdateMapObjectRequest;
import com.virtualoffice.workspace.dto.response.MapObjectResponse;
import com.virtualoffice.workspace.service.MapObjectService;
import com.virtualoffice.workspace.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Map Objects", description = "Interactive floor objects: computers and whiteboards")
@RestController
@RequestMapping("/api/workspace/{workspaceId}/map-objects")
public class MapObjectController {

    private final MapObjectService mapObjectService;

    public MapObjectController(MapObjectService mapObjectService) {
        this.mapObjectService = mapObjectService;
    }

    @Operation(summary = "Create a map object", description = "Requires ADMIN. Server generates the roomId.")
    @PostMapping
    public ResponseEntity<MapObjectResponse> create(@PathVariable Long workspaceId,
                                                    @Valid @RequestBody CreateMapObjectRequest request,
                                                    HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapObjectService.createMapObject(workspaceId, request, userId));
    }

    @Operation(summary = "List map objects", description = "Requires MEMBER.")
    @GetMapping
    public List<MapObjectResponse> list(@PathVariable Long workspaceId, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return mapObjectService.getMapObjects(workspaceId, userId);
    }

    @Operation(summary = "Update a map object", description = "Requires ADMIN. Partial update.")
    @PutMapping("/{id}")
    public MapObjectResponse update(@PathVariable Long workspaceId,
                                    @PathVariable Long id,
                                    @Valid @RequestBody UpdateMapObjectRequest request,
                                    HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return mapObjectService.updateMapObject(workspaceId, id, request, userId);
    }

    @Operation(summary = "Toggle active", description = "Requires ADMIN. Flips isActive.")
    @PatchMapping("/{id}/toggle")
    public MapObjectResponse toggle(@PathVariable Long workspaceId, @PathVariable Long id, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        return mapObjectService.toggleActive(workspaceId, id, userId);
    }

    @Operation(summary = "Delete a map object", description = "Requires ADMIN.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long id, HttpServletRequest http) {
        Long userId = UserContext.fromRequest(http).getUserId();
        mapObjectService.deleteMapObject(workspaceId, id, userId);
    }
}
