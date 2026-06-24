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
package com.virtualoffice.room_service.client;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over workspace-service's internal API (INTEGRATION.md §4.1, §4.3). room-service does
 * not store workspace roles or geometry; it asks workspace-service on demand so a user removed from
 * the workspace can no longer create or join voice rooms, and zone edits take effect quickly. All
 * calls authenticate with the shared {@code X-Internal-Token}.
 */
public interface WorkspaceClient {

    /**
     * Resolves a user's workspace role, or empty if they have no active desk there (404).
     */
    Optional<WorkspaceMemberRole> getMemberRole(int workspaceId, int userId);

    /**
     * Enforces that {@code userId} is an active member of {@code workspaceId} holding at least
     * {@code minRole}. Throws 403 otherwise (no active desk, insufficient role).
     */
    void requireRole(int workspaceId, int userId, WorkspaceRole minRole);

    /**
     * Returns the workspace's voice zones, cached briefly. Drives proximity/zone voice grouping
     * (§4.3); the cache spares workspace-service a call on every position batch.
     */
    List<Zone> getZones(int workspaceId);
}
