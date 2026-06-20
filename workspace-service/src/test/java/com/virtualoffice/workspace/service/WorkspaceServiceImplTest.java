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

import com.virtualoffice.workspace.dto.mapper.WorkspaceMapperImpl;
import com.virtualoffice.workspace.dto.request.CreateWorkspaceRequest;
import com.virtualoffice.workspace.dto.request.UpdateWorkspaceRequest;
import com.virtualoffice.workspace.dto.response.WorkspaceResponse;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.ForbiddenException;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.Workspace;
import com.virtualoffice.workspace.model.enums.InviteStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.model.enums.WorkspaceStatus;
import com.virtualoffice.workspace.repository.DeskRepository;
import com.virtualoffice.workspace.repository.WorkspaceRepository;
import com.virtualoffice.workspace.service.impl.WorkspaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceServiceImplTest {

    private WorkspaceRepository workspaceRepository;
    private DeskRepository deskRepository;
    private WorkspaceAccessGuard accessGuard;
    private WorkspaceServiceImpl service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        deskRepository = mock(DeskRepository.class);
        accessGuard = mock(WorkspaceAccessGuard.class);
        service = new WorkspaceServiceImpl(workspaceRepository, deskRepository, accessGuard, new WorkspaceMapperImpl());
    }

    private CreateWorkspaceRequest createRequest() {
        return new CreateWorkspaceRequest("Acme", "acme", "desc", null, "UTC");
    }

    @Test
    void createPersistsWorkspaceAndOwnerDesk() {
        when(workspaceRepository.existsBySlug("acme")).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> {
            Workspace w = inv.getArgument(0);
            w.setId(1L);
            return w;
        });

        WorkspaceResponse response = service.createWorkspace(createRequest(), 42L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.slug()).isEqualTo("acme");
        assertThat(response.ownerId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(WorkspaceStatus.ACTIVE);
        assertThat(response.inviteToken()).isNotNull();

        ArgumentCaptor<Desk> desk = ArgumentCaptor.forClass(Desk.class);
        verify(deskRepository).save(desk.capture());
        assertThat(desk.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(desk.getValue().getInviteStatus()).isEqualTo(InviteStatus.ACCEPTED);
        assertThat(desk.getValue().getUserId()).isEqualTo(42L);
        assertThat(desk.getValue().isActive()).isTrue();
    }

    @Test
    void createRejectsDuplicateSlug() {
        when(workspaceRepository.existsBySlug("acme")).thenReturn(true);
        assertThatThrownBy(() -> service.createWorkspace(createRequest(), 42L))
                .isInstanceOf(ConflictException.class);
        verify(workspaceRepository, never()).save(any());
        verify(deskRepository, never()).save(any());
    }

    @Test
    void updateRequiresAdminRole() {
        when(accessGuard.requireRole(1L, 9L, WorkspaceRole.ADMIN))
                .thenThrow(new ForbiddenException("nope"));
        assertThatThrownBy(() -> service.updateWorkspace(1L,
                new UpdateWorkspaceRequest("New", null, null, null), 9L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateAppliesNonNullFields() {
        Workspace existing = Workspace.builder().id(1L).name("Old").slug("acme")
                .ownerId(5L).status(WorkspaceStatus.ACTIVE).build();
        when(workspaceRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceResponse r = service.updateWorkspace(1L,
                new UpdateWorkspaceRequest("New Name", null, null, null), 5L);

        assertThat(r.name()).isEqualTo("New Name");
        assertThat(r.slug()).isEqualTo("acme"); // untouched
        verify(accessGuard).requireRole(1L, 5L, WorkspaceRole.ADMIN);
    }

    @Test
    void updateAppliesAllFields() {
        Workspace existing = Workspace.builder().id(1L).name("Old").slug("acme")
                .ownerId(5L).status(WorkspaceStatus.ACTIVE).build();
        when(workspaceRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceResponse r = service.updateWorkspace(1L,
                new UpdateWorkspaceRequest("N", "desc", "logo", "Africa/Cairo"), 5L);

        assertThat(r.name()).isEqualTo("N");
        assertThat(r.description()).isEqualTo("desc");
        assertThat(r.logoUrl()).isEqualTo("logo");
        assertThat(r.defaultTimezone()).isEqualTo("Africa/Cairo");
    }

    @Test
    void updateWithAllNullLeavesFieldsUnchanged() {
        Workspace existing = Workspace.builder().id(1L).name("Old").slug("acme")
                .ownerId(5L).description("keep").status(WorkspaceStatus.ACTIVE).build();
        when(workspaceRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceResponse r = service.updateWorkspace(1L,
                new UpdateWorkspaceRequest(null, null, null, null), 5L);

        assertThat(r.name()).isEqualTo("Old");
        assertThat(r.description()).isEqualTo("keep");
    }

    @Test
    void archiveIsOwnerOnly() {
        Workspace ws = Workspace.builder().id(1L).ownerId(5L).status(WorkspaceStatus.ACTIVE).build();
        when(workspaceRepository.findById(1L)).thenReturn(java.util.Optional.of(ws));

        assertThatThrownBy(() -> service.archiveWorkspace(1L, 9L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void archiveByOwnerSetsArchived() {
        Workspace ws = Workspace.builder().id(1L).ownerId(5L).status(WorkspaceStatus.ACTIVE).build();
        when(workspaceRepository.findById(1L)).thenReturn(java.util.Optional.of(ws));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceResponse r = service.archiveWorkspace(1L, 5L);
        assertThat(r.status()).isEqualTo(WorkspaceStatus.ARCHIVED);
    }

    @Test
    void rotateInviteTokenChangesToken() {
        UUID old = UUID.randomUUID();
        Workspace ws = Workspace.builder().id(1L).ownerId(5L).inviteToken(old)
                .status(WorkspaceStatus.ACTIVE).build();
        when(workspaceRepository.findById(1L)).thenReturn(java.util.Optional.of(ws));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceResponse r = service.rotateInviteToken(1L, 5L);
        assertThat(r.inviteToken()).isNotEqualTo(old);
        verify(accessGuard).requireRole(1L, 5L, WorkspaceRole.ADMIN);
    }
}
