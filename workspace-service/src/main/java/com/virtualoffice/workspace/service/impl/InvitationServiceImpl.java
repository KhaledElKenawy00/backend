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
package com.virtualoffice.workspace.service.impl;

import com.virtualoffice.workspace.dto.mapper.InvitationMapper;
import com.virtualoffice.workspace.dto.request.InviteMemberRequest;
import com.virtualoffice.workspace.dto.response.InvitationResponse;
import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.GoneException;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.WorkspaceInvitation;
import com.virtualoffice.workspace.model.enums.AvatarCharacter;
import com.virtualoffice.workspace.model.enums.DeskStatus;
import com.virtualoffice.workspace.model.enums.InviteStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.DeskRepository;
import com.virtualoffice.workspace.repository.InvitationRepository;
import com.virtualoffice.workspace.service.InvitationService;
import com.virtualoffice.workspace.service.WorkspaceAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class InvitationServiceImpl implements InvitationService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final InvitationRepository invitationRepository;
    private final DeskRepository deskRepository;
    private final WorkspaceAccessGuard accessGuard;
    private final InvitationMapper mapper;

    public InvitationServiceImpl(InvitationRepository invitationRepository,
                                 DeskRepository deskRepository,
                                 WorkspaceAccessGuard accessGuard,
                                 InvitationMapper mapper) {
        this.invitationRepository = invitationRepository;
        this.deskRepository = deskRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
    }

    @Override
    public InvitationResponse invite(Long workspaceId, InviteMemberRequest request, Long invitedBy) {
        accessGuard.requireRole(workspaceId, invitedBy, WorkspaceRole.ADMIN);
        if (request.role() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("cannot invite a member as OWNER");
        }
        invitationRepository.findByWorkspaceIdAndInvitedEmailAndStatus(
                        workspaceId, request.email(), InviteStatus.PENDING)
                .ifPresent(inv -> {
                    throw new ConflictException("a pending invitation already exists for " + request.email());
                });

        WorkspaceInvitation invitation = invitationRepository.save(WorkspaceInvitation.builder()
                .workspaceId(workspaceId)
                .invitedEmail(request.email())
                .invitedBy(invitedBy)
                .token(UUID.randomUUID())
                .role(request.role())
                .status(InviteStatus.PENDING)
                .expiresAt(Instant.now().plus(INVITE_TTL))
                .build());

        // notifications-service publishes the invite email off a RabbitMQ event (see INTEGRATION.md);
        // wiring the broker is tracked separately so this milestone stays broker-free.
        return mapper.toResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> getInvitations(Long workspaceId, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        return mapper.toResponseList(invitationRepository.findByWorkspaceId(workspaceId));
    }

    @Override
    public void revokeInvitation(Long workspaceId, Long invitationId, Long requesterId) {
        accessGuard.requireRole(workspaceId, requesterId, WorkspaceRole.ADMIN);
        WorkspaceInvitation invitation = invitationRepository.findByIdAndWorkspaceId(invitationId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("invitation not found: " + invitationId));
        invitationRepository.delete(invitation);
    }

    @Override
    public InvitationResponse acceptInvite(String token, Long acceptingUserId) {
        WorkspaceInvitation invitation = findByToken(token);

        if (invitation.getStatus() == InviteStatus.PENDING && isExpired(invitation)) {
            invitation.setStatus(InviteStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new GoneException("invitation has expired");
        }
        if (invitation.getStatus() != InviteStatus.PENDING) {
            throw new ConflictException("invitation is no longer pending: " + invitation.getStatus());
        }

        activateMembership(invitation, acceptingUserId);
        invitation.setStatus(InviteStatus.ACCEPTED);
        return mapper.toResponse(invitationRepository.save(invitation));
    }

    @Override
    public InvitationResponse declineInvite(String token) {
        WorkspaceInvitation invitation = findByToken(token);
        if (invitation.getStatus() != InviteStatus.PENDING) {
            throw new ConflictException("invitation is no longer pending: " + invitation.getStatus());
        }
        invitation.setStatus(InviteStatus.DECLINED);
        return mapper.toResponse(invitationRepository.save(invitation));
    }

    // --- helpers ---

    // The Desk is created on acceptance, when the accepting user's id is known (invites are by
    // email and the user may not have existed in user-service at invite time).
    private void activateMembership(WorkspaceInvitation invitation, Long userId) {
        Desk existing = deskRepository.findByWorkspaceIdAndUserId(invitation.getWorkspaceId(), userId).orElse(null);
        if (existing != null) {
            if (existing.isActive()) {
                throw new ConflictException("user is already a member of this workspace");
            }
            existing.setActive(true);
            existing.setRole(invitation.getRole());
            existing.setInviteStatus(InviteStatus.ACCEPTED);
            existing.setJoinedAt(Instant.now());
            deskRepository.save(existing);
            return;
        }
        deskRepository.save(Desk.builder()
                .workspaceId(invitation.getWorkspaceId())
                .userId(userId)
                .workEmail(invitation.getInvitedEmail())
                .avatarCharacter(AvatarCharacter.ADAM)
                .status(DeskStatus.ACTIVE)
                .role(invitation.getRole())
                .inviteStatus(InviteStatus.ACCEPTED)
                .invitedBy(invitation.getInvitedBy())
                .positionX(0)
                .positionY(0)
                .isOnline(false)
                .isActive(true)
                .joinedAt(Instant.now())
                .build());
    }

    private boolean isExpired(WorkspaceInvitation invitation) {
        return invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(Instant.now());
    }

    private WorkspaceInvitation findByToken(String token) {
        UUID uuid;
        try {
            uuid = UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("invalid invitation token");
        }
        return invitationRepository.findByToken(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("invitation not found"));
    }
}
