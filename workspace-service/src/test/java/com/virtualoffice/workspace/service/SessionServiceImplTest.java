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
import com.virtualoffice.workspace.dto.mapper.MapObjectMapperImpl;
import com.virtualoffice.workspace.dto.request.PresenceSyncRequest;
import com.virtualoffice.workspace.dto.response.JoinValidationResponse;
import com.virtualoffice.workspace.dto.response.MemberRoleResponse;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.enums.AvatarCharacter;
import com.virtualoffice.workspace.model.enums.DeskStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.DeskRepository;
import com.virtualoffice.workspace.repository.MapObjectRepository;
import com.virtualoffice.workspace.repository.WorkspaceRepository;
import com.virtualoffice.workspace.repository.ZoneRepository;
import com.virtualoffice.workspace.service.impl.SessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceImplTest {

    private WorkspaceRepository workspaceRepository;
    private DeskRepository deskRepository;
    private ZoneRepository zoneRepository;
    private SessionServiceImpl service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        deskRepository = mock(DeskRepository.class);
        zoneRepository = mock(ZoneRepository.class);
        service = new SessionServiceImpl(workspaceRepository, deskRepository,
                mock(MapObjectRepository.class), zoneRepository, mock(LayoutService.class),
                new DeskMapperImpl(), new MapObjectMapperImpl());
    }

    private Desk activeDesk() {
        return Desk.builder().id(10L).workspaceId(1L).userId(5L).role(WorkspaceRole.ADMIN)
                .avatarCharacter(AvatarCharacter.LUCY).status(DeskStatus.ACTIVE)
                .positionX(100).positionY(200).isActive(true).build();
    }

    @Test
    void validateJoinReturnsIdentityForActiveMember() {
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.of(activeDesk()));
        JoinValidationResponse r = service.validateJoin(1L, 5L);
        assertThat(r.allowed()).isTrue();
        assertThat(r.deskId()).isEqualTo(10L);
        assertThat(r.avatarCharacter()).isEqualTo(AvatarCharacter.LUCY);
        assertThat(r.spawnX()).isEqualTo(100);
        assertThat(r.role()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    void validateJoinForNonMemberIsNotFound() {
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.validateJoin(1L, 99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void memberRoleReturnsRole() {
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.of(activeDesk()));
        MemberRoleResponse r = service.getMemberRole(1L, 5L);
        assertThat(r.role()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(r.isActive()).isTrue();
    }

    @Test
    void memberRoleForInactiveDeskIsNotFound() {
        Desk inactive = activeDesk();
        inactive.setActive(false);
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.getMemberRole(1L, 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void syncPresenceUpdatesOnlineAndPosition() {
        Desk desk = activeDesk();
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.of(desk));
        when(deskRepository.save(any(Desk.class))).thenAnswer(i -> i.getArgument(0));

        service.syncPresence(1L, new PresenceSyncRequest(5L, true, DeskStatus.AWAY, "☕", 7, 8));

        ArgumentCaptor<Desk> saved = ArgumentCaptor.forClass(Desk.class);
        verify(deskRepository).save(saved.capture());
        assertThat(saved.getValue().isOnline()).isTrue();
        assertThat(saved.getValue().getPositionX()).isEqualTo(7);
        assertThat(saved.getValue().getStatus()).isEqualTo(DeskStatus.AWAY);
        assertThat(saved.getValue().getLastSeenAt()).isNotNull();
    }

    @Test
    void syncPresenceForMissingDeskIsNotFound() {
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.syncPresence(1L, new PresenceSyncRequest(5L, true, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void chatContextReturnsChannelKey() {
        when(workspaceRepository.existsById(1L)).thenReturn(true);
        assertThat(service.getChatContext(1L).channelKey()).isEqualTo("workspace:1");
    }
}
