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
package com.virtualoffice.workspace.repository;

import com.virtualoffice.workspace.AbstractIntegrationTest;
import com.virtualoffice.workspace.Fixtures;
import com.virtualoffice.workspace.model.WorkspaceInvitation;
import com.virtualoffice.workspace.model.enums.InviteStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class InvitationRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    InvitationRepository invitationRepository;

    private WorkspaceInvitation invite(Long wid, String email, InviteStatus status) {
        return WorkspaceInvitation.builder()
                .workspaceId(wid)
                .invitedEmail(email)
                .invitedBy(1L)
                .token(UUID.randomUUID())
                .role(WorkspaceRole.MEMBER)
                .status(status)
                .build();
    }

    @Test
    void rejectsDuplicatePendingInviteForSameEmail() {
        Long wid = workspaceRepository.save(Fixtures.workspace("inv-" + System.nanoTime()).build()).getId();
        invitationRepository.saveAndFlush(invite(wid, "a@x.com", InviteStatus.PENDING));
        assertThatThrownBy(() ->
                invitationRepository.saveAndFlush(invite(wid, "a@x.com", InviteStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsNewPendingAfterPreviousDeclined() {
        Long wid = workspaceRepository.save(Fixtures.workspace("inv-" + System.nanoTime()).build()).getId();
        invitationRepository.saveAndFlush(invite(wid, "b@x.com", InviteStatus.DECLINED));
        invitationRepository.saveAndFlush(invite(wid, "b@x.com", InviteStatus.PENDING));

        assertThat(invitationRepository.findByWorkspaceIdAndInvitedEmailAndStatus(
                wid, "b@x.com", InviteStatus.PENDING)).isPresent();
    }
}
