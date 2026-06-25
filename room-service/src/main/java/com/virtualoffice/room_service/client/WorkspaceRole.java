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

/**
 * A user's role within a workspace, as owned by workspace-service. Names must match the producer's
 * enum so role strings deserialize directly. {@link #rank} gives an order for {@code >=} checks
 * (e.g. "ADMIN or higher"). This is the workspace-scoped role, distinct from the account-level
 * {@code X-User-Role} (USER/ADMIN) carried in request headers.
 */
public enum WorkspaceRole {
    GUEST(0),
    MEMBER(1),
    ADMIN(2),
    OWNER(3);

    private final int rank;

    WorkspaceRole(int rank) {
        this.rank = rank;
    }

    public boolean isAtLeast(WorkspaceRole other) {
        return this.rank >= other.rank;
    }
}
