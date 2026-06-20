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
package com.virtualoffice.workspace.service;

import com.virtualoffice.workspace.exception.ForbiddenException;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.DeskRepository;
import org.springframework.stereotype.Component;

/**
 * Single source of workspace-scoped authorization. Every service reuses these checks instead
 * of re-deriving role logic. A user's authority comes from their active Desk in the workspace.
 */
@Component
public class WorkspaceAccessGuard {

    private final DeskRepository deskRepository;

    public WorkspaceAccessGuard(DeskRepository deskRepository) {
        this.deskRepository = deskRepository;
    }

    /** Returns the caller's active desk, or 403 if they have none in this workspace. */
    public Desk requireMember(Long workspaceId, Long userId) {
        return deskRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(Desk::isActive)
                .orElseThrow(() -> new ForbiddenException("not an active member of this workspace"));
    }

    /** Returns the caller's active desk if their role is at least {@code min}, else 403. */
    public Desk requireRole(Long workspaceId, Long userId, WorkspaceRole min) {
        Desk desk = requireMember(workspaceId, userId);
        if (!desk.getRole().atLeast(min)) {
            throw new ForbiddenException("requires role " + min + " or higher");
        }
        return desk;
    }
}
