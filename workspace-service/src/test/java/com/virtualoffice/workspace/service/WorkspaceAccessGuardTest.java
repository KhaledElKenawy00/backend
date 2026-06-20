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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceAccessGuardTest {

    private DeskRepository deskRepository;
    private WorkspaceAccessGuard guard;

    @BeforeEach
    void setUp() {
        deskRepository = mock(DeskRepository.class);
        guard = new WorkspaceAccessGuard(deskRepository);
    }

    private Desk desk(WorkspaceRole role, boolean active) {
        return Desk.builder().id(1L).workspaceId(1L).userId(5L).role(role).isActive(active).build();
    }

    @Test
    void requireMemberReturnsActiveDesk() {
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.of(desk(WorkspaceRole.MEMBER, true)));
        assertThat(guard.requireMember(1L, 5L).getUserId()).isEqualTo(5L);
    }

    @Test
    void requireMemberRejectsNoDesk() {
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> guard.requireMember(1L, 5L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireMemberRejectsInactiveDesk() {
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.of(desk(WorkspaceRole.MEMBER, false)));
        assertThatThrownBy(() -> guard.requireMember(1L, 5L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRoleAllowsSufficientRole() {
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.of(desk(WorkspaceRole.ADMIN, true)));
        assertThat(guard.requireRole(1L, 5L, WorkspaceRole.ADMIN).getRole()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    void requireRoleRejectsInsufficientRole() {
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.of(desk(WorkspaceRole.MEMBER, true)));
        assertThatThrownBy(() -> guard.requireRole(1L, 5L, WorkspaceRole.ADMIN)).isInstanceOf(ForbiddenException.class);
    }
}
