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
package com.virtualoffice.workspace.model.enums;

/**
 * Workspace-scoped role carried on a Desk. The same user can hold a different role
 * in each workspace. {@code rank} gives a total order so authorization checks can say
 * "at least ADMIN" via {@link #atLeast(WorkspaceRole)} instead of enumerating roles.
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

    public boolean atLeast(WorkspaceRole other) {
        return this.rank >= other.rank;
    }
}
