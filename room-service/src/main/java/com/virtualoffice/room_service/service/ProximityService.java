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
package com.virtualoffice.room_service.service;

import com.virtualoffice.room_service.dto.request.PlayerPosition;

import java.util.List;
import java.util.Map;

/**
 * Maintains live avatar positions per workspace and resolves them into proximity/zone voice groups
 * (INTEGRATION.md §4.3), broadcasting the changes to clients.
 */
public interface ProximityService {

    /**
     * Replaces the workspace's position snapshot, recomputes voice channels, broadcasts the
     * difference from the previous assignment, and returns the full current assignment
     * (userId → Agora channel) for avatars that are in a channel.
     */
    Map<Integer, String> updatePositions(int workspaceId, List<PlayerPosition> positions);
}
