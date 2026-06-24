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
package com.virtualoffice.room_service.controller;

import com.virtualoffice.room_service.dto.request.PositionBatchRequest;
import com.virtualoffice.room_service.service.ProximityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Server-to-server endpoint consumed by the forked SkyOffice Colyseus server (INTEGRATION.md §4.2).
 * Under {@code /api/internal/**} — blocked at the gateway and guarded by {@code InternalAuthFilter}
 * ({@code X-Internal-Token}); there is no end-user identity here.
 */
@RestController
@RequestMapping("/api/internal/workspace/{workspaceId}")
@RequiredArgsConstructor
public class PositionController {

    private final ProximityService proximityService;

    /**
     * Accepts the workspace's current avatar-position snapshot and returns the resulting voice
     * assignment (userId → Agora channel). Changes are also broadcast over STOMP.
     */
    @PostMapping("/positions")
    public Map<Integer, String> updatePositions(@PathVariable Integer workspaceId,
                                                @Valid @RequestBody PositionBatchRequest request) {
        return proximityService.updatePositions(workspaceId, request.positions());
    }
}
