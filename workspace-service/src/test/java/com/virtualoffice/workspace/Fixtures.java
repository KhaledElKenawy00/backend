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
package com.virtualoffice.workspace;

import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.Workspace;
import com.virtualoffice.workspace.model.enums.AvatarCharacter;
import com.virtualoffice.workspace.model.enums.DeskStatus;
import com.virtualoffice.workspace.model.enums.InviteStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.model.enums.WorkspaceStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceVisibility;

import java.util.UUID;

/** Builders for valid persisted entities, so tests only set what they care about. */
public final class Fixtures {

    private Fixtures() {}

    public static Workspace.WorkspaceBuilder workspace(String slug) {
        return Workspace.builder()
                .name("Acme " + slug)
                .slug(slug)
                .ownerId(1L)
                .status(WorkspaceStatus.ACTIVE)
                .visibility(WorkspaceVisibility.INVITE_ONLY)
                .inviteToken(UUID.randomUUID())
                .defaultTimezone("UTC")
                .tileSize(32)
                .mapWidth(80)
                .mapHeight(60);
    }

    public static Desk.DeskBuilder desk(Long workspaceId, Long userId) {
        return Desk.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .fullName("User " + userId)
                .avatarCharacter(AvatarCharacter.ADAM)
                .status(DeskStatus.ACTIVE)
                .role(WorkspaceRole.MEMBER)
                .inviteStatus(InviteStatus.ACCEPTED)
                .positionX(0)
                .positionY(0)
                .isActive(true);
    }
}
