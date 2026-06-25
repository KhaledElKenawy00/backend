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
package com.virtualoffice.workspace.dto.response;

import com.virtualoffice.workspace.model.enums.AvatarCharacter;
import com.virtualoffice.workspace.model.enums.DeskStatus;
import com.virtualoffice.workspace.model.enums.InviteStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;

import java.time.Instant;
import java.util.List;

public record DeskResponse(
        Long id,
        Long userId,
        Long workspaceId,
        String fullName,
        String nickName,
        String title,
        String workEmail,
        String phone,
        String personalImageUrl,
        AvatarCharacter avatarCharacter,
        String timezone,
        DeskStatus status,
        String statusEmoji,
        String statusCustomText,
        Integer positionX,
        Integer positionY,
        boolean isOnline,
        Instant lastSeenAt,
        WorkspaceRole role,
        String bio,
        Long teamId,
        InviteStatus inviteStatus,
        boolean isActive,
        Instant joinedAt,
        List<String> links,
        List<DeskWidgetResponse> widgets) {
}
