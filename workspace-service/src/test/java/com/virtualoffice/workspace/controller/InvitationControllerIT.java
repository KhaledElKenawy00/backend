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
package com.virtualoffice.workspace.controller;

import com.virtualoffice.workspace.AbstractIntegrationTest;
import com.virtualoffice.workspace.dto.request.InviteMemberRequest;
import com.virtualoffice.workspace.dto.response.InvitationResponse;
import com.virtualoffice.workspace.model.enums.InviteStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private ResponseEntity<InvitationResponse> invite(Long wid, long admin, String email) {
        return rest.exchange("/api/workspace/" + wid + "/invitations", HttpMethod.POST,
                new HttpEntity<>(new InviteMemberRequest(email, WorkspaceRole.MEMBER), userHeaders(admin)),
                InvitationResponse.class);
    }

    @Test
    void adminInvitesAndInviteeAccepts() {
        Long wid = createWorkspace(rest, 1L).id();

        ResponseEntity<InvitationResponse> invited = invite(wid, 1L, "new@x.com");
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(invited.getBody().status()).isEqualTo(InviteStatus.PENDING);

        String token = invited.getBody().token().toString();
        ResponseEntity<InvitationResponse> accepted = rest.exchange(
                "/api/invitations/accept?token=" + token, HttpMethod.POST,
                new HttpEntity<>(userHeaders(200L)), InvitationResponse.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody().status()).isEqualTo(InviteStatus.ACCEPTED);

        // accepting user is now a member
        ResponseEntity<String> me = rest.exchange("/api/workspace/" + wid + "/desks/me",
                HttpMethod.GET, new HttpEntity<>(userHeaders(200L)), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void duplicatePendingInviteReturns409() {
        Long wid = createWorkspace(rest, 2L).id();
        assertThat(invite(wid, 2L, "dup@x.com").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // read the duplicate as String — the 409 error body isn't an InvitationResponse shape
        ResponseEntity<String> dup = rest.exchange("/api/workspace/" + wid + "/invitations", HttpMethod.POST,
                new HttpEntity<>(new InviteMemberRequest("dup@x.com", WorkspaceRole.MEMBER), userHeaders(2L)),
                String.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nonAdminCannotInvite() {
        Long wid = createWorkspace(rest, 3L).id();
        ResponseEntity<String> r = rest.exchange("/api/workspace/" + wid + "/invitations", HttpMethod.POST,
                new HttpEntity<>(new InviteMemberRequest("x@x.com", WorkspaceRole.MEMBER), userHeaders(999L)),
                String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void acceptInvalidTokenReturns404() {
        ResponseEntity<String> r = rest.exchange("/api/invitations/accept?token=" + java.util.UUID.randomUUID(),
                HttpMethod.POST, new HttpEntity<>(userHeaders(5L)), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void declineThenAcceptIsConflict() {
        Long wid = createWorkspace(rest, 6L).id();
        String token = invite(wid, 6L, "dec@x.com").getBody().token().toString();

        ResponseEntity<InvitationResponse> declined = rest.exchange(
                "/api/invitations/decline?token=" + token, HttpMethod.POST,
                new HttpEntity<>(userHeaders(60L)), InvitationResponse.class);
        assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(declined.getBody().status()).isEqualTo(InviteStatus.DECLINED);

        ResponseEntity<String> accept = rest.exchange("/api/invitations/accept?token=" + token,
                HttpMethod.POST, new HttpEntity<>(userHeaders(60L)), String.class);
        assertThat(accept.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void adminListsAndRevokesInvitations() {
        Long wid = createWorkspace(rest, 7L).id();
        Long invId = invite(wid, 7L, "rev@x.com").getBody().id();

        ResponseEntity<InvitationResponse[]> list = rest.exchange("/api/workspace/" + wid + "/invitations",
                HttpMethod.GET, new HttpEntity<>(userHeaders(7L)), InvitationResponse[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).extracting(InvitationResponse::id).contains(invId);

        ResponseEntity<Void> revoked = rest.exchange("/api/workspace/" + wid + "/invitations/" + invId,
                HttpMethod.DELETE, new HttpEntity<>(userHeaders(7L)), Void.class);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
