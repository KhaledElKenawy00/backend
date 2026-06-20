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
package com.virtualoffice.workspace.model;

import com.virtualoffice.workspace.model.enums.AvatarCharacter;
import com.virtualoffice.workspace.model.enums.DeskStatus;
import com.virtualoffice.workspace.model.enums.InviteStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A Desk is a user's membership + workspace-specific profile + spatial position.
 * User (user-service) + Desk (here) = a person sitting in a specific office.
 * Unique per (userId, workspaceId).
 */
@Entity
@Table(name = "desk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Desk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long workspaceId;

    private String fullName;

    private String nickName;

    private String title;

    private String workEmail;

    private String phone;

    private String personalImageUrl;

    @Enumerated(EnumType.STRING)
    private AvatarCharacter avatarCharacter;

    private String timezone;

    @Enumerated(EnumType.STRING)
    private DeskStatus status;

    private String statusEmoji;

    private String statusCustomText;

    // explicit: CamelCaseToUnderscoresNamingStrategy won't split a trailing capital (positionX -> positionx)
    @Column(name = "position_x")
    private Integer positionX;

    @Column(name = "position_y")
    private Integer positionY;

    // Cached presence flag, synced from Colyseus. Not the source of truth — verify lastSeenAt.
    private boolean isOnline;

    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    private WorkspaceRole role;

    private String bio;

    private Long teamId;

    @Enumerated(EnumType.STRING)
    private InviteStatus inviteStatus;

    private Long invitedBy;

    private boolean isActive;

    private Instant joinedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
