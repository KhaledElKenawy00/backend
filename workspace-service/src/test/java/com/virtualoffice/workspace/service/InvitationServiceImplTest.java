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

import com.virtualoffice.workspace.dto.mapper.InvitationMapperImpl;
import com.virtualoffice.workspace.dto.request.InviteMemberRequest;
import com.virtualoffice.workspace.dto.response.InvitationResponse;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.GoneException;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.WorkspaceInvitation;
import com.virtualoffice.workspace.model.enums.InviteStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.DeskRepository;
import com.virtualoffice.workspace.repository.InvitationRepository;
import com.virtualoffice.workspace.service.impl.InvitationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvitationServiceImplTest {

    private InvitationRepository invitationRepository;
    private DeskRepository deskRepository;
    private WorkspaceAccessGuard accessGuard;
    private InvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        invitationRepository = mock(InvitationRepository.class);
        deskRepository = mock(DeskRepository.class);
        accessGuard = mock(WorkspaceAccessGuard.class);
        service = new InvitationServiceImpl(invitationRepository, deskRepository, accessGuard, new InvitationMapperImpl());
    }

    private WorkspaceInvitation invitation(InviteStatus status, Instant expiresAt) {
        return WorkspaceInvitation.builder()
                .id(1L).workspaceId(1L).invitedEmail("a@x.com").invitedBy(1L)
                .token(UUID.randomUUID()).role(WorkspaceRole.MEMBER).status(status).expiresAt(expiresAt)
                .build();
    }

    @Test
    void inviteCreatesPendingInvitation() {
        when(invitationRepository.findByWorkspaceIdAndInvitedEmailAndStatus(1L, "a@x.com", InviteStatus.PENDING))
                .thenReturn(Optional.empty());
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(i -> i.getArgument(0));

        InvitationResponse r = service.invite(1L, new InviteMemberRequest("a@x.com", WorkspaceRole.MEMBER), 1L);

        assertThat(r.status()).isEqualTo(InviteStatus.PENDING);
        assertThat(r.token()).isNotNull();
        assertThat(r.expiresAt()).isAfter(Instant.now());
        verify(accessGuard).requireRole(1L, 1L, WorkspaceRole.ADMIN);
    }

    @Test
    void cannotInviteAsOwner() {
        assertThatThrownBy(() -> service.invite(1L, new InviteMemberRequest("a@x.com", WorkspaceRole.OWNER), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicatePendingInvite() {
        when(invitationRepository.findByWorkspaceIdAndInvitedEmailAndStatus(1L, "a@x.com", InviteStatus.PENDING))
                .thenReturn(Optional.of(invitation(InviteStatus.PENDING, Instant.now().plusSeconds(60))));
        assertThatThrownBy(() -> service.invite(1L, new InviteMemberRequest("a@x.com", WorkspaceRole.MEMBER), 1L))
                .isInstanceOf(ConflictException.class);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void acceptCreatesDeskAndMarksAccepted() {
        WorkspaceInvitation inv = invitation(InviteStatus.PENDING, Instant.now().plus(1, ChronoUnit.DAYS));
        when(invitationRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 77L)).thenReturn(Optional.empty());
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(i -> i.getArgument(0));

        InvitationResponse r = service.acceptInvite(inv.getToken().toString(), 77L);

        assertThat(r.status()).isEqualTo(InviteStatus.ACCEPTED);
        verify(deskRepository).save(any(Desk.class));
    }

    @Test
    void acceptReactivatesInactiveDesk() {
        WorkspaceInvitation inv = invitation(InviteStatus.PENDING, Instant.now().plusSeconds(60));
        Desk inactive = Desk.builder().id(5L).workspaceId(1L).userId(77L)
                .role(WorkspaceRole.GUEST).isActive(false).build();
        when(invitationRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 77L)).thenReturn(Optional.of(inactive));
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(i -> i.getArgument(0));

        service.acceptInvite(inv.getToken().toString(), 77L);

        assertThat(inactive.isActive()).isTrue();
        assertThat(inactive.getRole()).isEqualTo(WorkspaceRole.MEMBER); // from the invitation
        verify(deskRepository).save(inactive);
    }

    @Test
    void acceptWhenAlreadyActiveMemberIsConflict() {
        WorkspaceInvitation inv = invitation(InviteStatus.PENDING, Instant.now().plusSeconds(60));
        Desk active = Desk.builder().id(5L).workspaceId(1L).userId(77L)
                .role(WorkspaceRole.MEMBER).isActive(true).build();
        when(invitationRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 77L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.acceptInvite(inv.getToken().toString(), 77L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptInviteWithNoExpiryNeverExpires() {
        WorkspaceInvitation inv = invitation(InviteStatus.PENDING, null); // expiresAt == null
        when(invitationRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(deskRepository.findByWorkspaceIdAndUserId(1L, 77L)).thenReturn(Optional.empty());
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(i -> i.getArgument(0));

        InvitationResponse r = service.acceptInvite(inv.getToken().toString(), 77L);
        assertThat(r.status()).isEqualTo(InviteStatus.ACCEPTED);
    }

    @Test
    void acceptExpiredInviteIsGone() {
        WorkspaceInvitation inv = invitation(InviteStatus.PENDING, Instant.now().minusSeconds(60));
        when(invitationRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.acceptInvite(inv.getToken().toString(), 77L))
                .isInstanceOf(GoneException.class);
        assertThat(inv.getStatus()).isEqualTo(InviteStatus.EXPIRED);
        verify(deskRepository, never()).save(any());
    }

    @Test
    void acceptInvalidTokenIsNotFound() {
        assertThatThrownBy(() -> service.acceptInvite("not-a-uuid", 77L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void acceptAlreadyAcceptedIsConflict() {
        WorkspaceInvitation inv = invitation(InviteStatus.ACCEPTED, Instant.now().plusSeconds(60));
        when(invitationRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        assertThatThrownBy(() -> service.acceptInvite(inv.getToken().toString(), 77L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void declineMarksDeclined() {
        WorkspaceInvitation inv = invitation(InviteStatus.PENDING, Instant.now().plusSeconds(60));
        when(invitationRepository.findByToken(inv.getToken())).thenReturn(Optional.of(inv));
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(i -> i.getArgument(0));

        InvitationResponse r = service.declineInvite(inv.getToken().toString());
        assertThat(r.status()).isEqualTo(InviteStatus.DECLINED);
    }

    @Test
    void revokeMissingInvitationIsNotFound() {
        when(invitationRepository.findByIdAndWorkspaceId(9L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.revokeInvitation(1L, 9L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
