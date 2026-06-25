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

import com.virtualoffice.workspace.dto.mapper.DeskMapperImpl;
import com.virtualoffice.workspace.dto.request.UpdateDeskRequest;
import com.virtualoffice.workspace.dto.request.UpdateStatusRequest;
import com.virtualoffice.workspace.dto.response.DeskResponse;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.ForbiddenException;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.messaging.WorkspaceChannelEventPublisher;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.enums.DeskStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.DeskLinkRepository;
import com.virtualoffice.workspace.repository.DeskRepository;
import com.virtualoffice.workspace.repository.DeskWidgetRepository;
import com.virtualoffice.workspace.service.impl.DeskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeskServiceImplTest {

    private DeskRepository deskRepository;
    private DeskLinkRepository deskLinkRepository;
    private DeskWidgetRepository deskWidgetRepository;
    private WorkspaceAccessGuard accessGuard;
    private WorkspaceChannelEventPublisher channelEvents;
    private DeskServiceImpl service;

    @BeforeEach
    void setUp() {
        deskRepository = mock(DeskRepository.class);
        deskLinkRepository = mock(DeskLinkRepository.class);
        deskWidgetRepository = mock(DeskWidgetRepository.class);
        accessGuard = mock(WorkspaceAccessGuard.class);
        channelEvents = mock(WorkspaceChannelEventPublisher.class);
        service = new DeskServiceImpl(deskRepository, deskLinkRepository, deskWidgetRepository,
                accessGuard, new DeskMapperImpl(), channelEvents);
        when(deskLinkRepository.findByDeskId(anyLong())).thenReturn(List.of());
        when(deskWidgetRepository.findByDeskIdOrderByPositionAsc(anyLong())).thenReturn(List.of());
    }

    private Desk desk(Long id, Long userId, WorkspaceRole role) {
        return Desk.builder().id(id).workspaceId(1L).userId(userId).role(role)
                .status(DeskStatus.ACTIVE).isActive(true).build();
    }

    @Test
    void getMyDeskReturnsCallerDesk() {
        when(accessGuard.requireMember(1L, 5L)).thenReturn(desk(10L, 5L, WorkspaceRole.MEMBER));
        DeskResponse r = service.getMyDesk(1L, 5L);
        assertThat(r.id()).isEqualTo(10L);
        assertThat(r.userId()).isEqualTo(5L);
    }

    @Test
    void updateOwnDeskAppliesFields() {
        when(deskRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(desk(10L, 5L, WorkspaceRole.MEMBER)));
        when(deskRepository.save(org.mockito.ArgumentMatchers.any(Desk.class))).thenAnswer(i -> i.getArgument(0));

        DeskResponse r = service.updateDesk(1L, 10L,
                new UpdateDeskRequest("New Name", null, null, null, null, null, null, null, null), 5L);

        assertThat(r.fullName()).isEqualTo("New Name");
    }

    @Test
    void updateReplacesLinksAndWidgets() {
        when(deskRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(desk(10L, 5L, WorkspaceRole.MEMBER)));
        when(deskRepository.save(org.mockito.ArgumentMatchers.any(Desk.class))).thenAnswer(i -> i.getArgument(0));

        service.updateDesk(1L, 10L, new UpdateDeskRequest(null, "Nick", null, "bio", null, "UTC", 3L,
                java.util.List.of("https://a", "https://a", "https://b"),
                java.util.List.of(new UpdateDeskRequest.WidgetInput("CLOCK", "Clock", 0, "{}"))), 5L);

        verify(deskLinkRepository).deleteByDeskId(10L);
        verify(deskWidgetRepository).deleteByDeskId(10L);
        // duplicate link URLs are de-duplicated before saving
        verify(deskLinkRepository, org.mockito.Mockito.times(2))
                .save(org.mockito.ArgumentMatchers.any());
        verify(deskWidgetRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getDeskByIdRequiresMembership() {
        when(deskRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(desk(10L, 5L, WorkspaceRole.MEMBER)));
        DeskResponse r = service.getDesk(1L, 10L, 999L);
        assertThat(r.id()).isEqualTo(10L);
        verify(accessGuard).requireMember(1L, 999L);
    }

    @Test
    void cannotUpdateAnotherUsersDesk() {
        when(deskRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(desk(10L, 5L, WorkspaceRole.MEMBER)));
        assertThatThrownBy(() -> service.updateDesk(1L, 10L,
                new UpdateDeskRequest("x", null, null, null, null, null, null, null, null), 999L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateMissingDeskThrowsNotFound() {
        when(deskRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateStatus(1L, 10L,
                new UpdateStatusRequest(DeskStatus.AWAY, null, null), 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatusSetsStatus() {
        when(deskRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(desk(10L, 5L, WorkspaceRole.MEMBER)));
        when(deskRepository.save(org.mockito.ArgumentMatchers.any(Desk.class))).thenAnswer(i -> i.getArgument(0));

        DeskResponse r = service.updateStatus(1L, 10L,
                new UpdateStatusRequest(DeskStatus.DO_NOT_DISTURB, "🔕", "heads down"), 5L);

        assertThat(r.status()).isEqualTo(DeskStatus.DO_NOT_DISTURB);
        assertThat(r.statusCustomText()).isEqualTo("heads down");
    }

    @Test
    void removeMemberSoftDeletes() {
        Desk member = desk(10L, 5L, WorkspaceRole.MEMBER);
        when(deskRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(member));
        service.removeMember(1L, 10L, 1L);
        assertThat(member.isActive()).isFalse();
        verify(accessGuard).requireRole(1L, 1L, WorkspaceRole.ADMIN);
        verify(deskRepository).save(member);
        verify(channelEvents).memberRemoved(1L, 5L);
    }

    @Test
    void cannotRemoveOwner() {
        when(deskRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(desk(10L, 5L, WorkspaceRole.OWNER)));
        assertThatThrownBy(() -> service.removeMember(1L, 10L, 1L))
                .isInstanceOf(ConflictException.class);
    }
}
