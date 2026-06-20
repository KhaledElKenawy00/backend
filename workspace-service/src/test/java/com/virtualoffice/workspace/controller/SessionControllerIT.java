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
import com.virtualoffice.workspace.dto.request.PresenceSyncRequest;
import com.virtualoffice.workspace.dto.response.ChatContextResponse;
import com.virtualoffice.workspace.dto.response.JoinValidationResponse;
import com.virtualoffice.workspace.dto.response.MemberRoleResponse;
import com.virtualoffice.workspace.dto.response.SessionConfigResponse;
import com.virtualoffice.workspace.model.enums.DeskStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SessionControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private String base(Long wid) {
        return "/api/internal/workspace/" + wid;
    }

    @Test
    void sessionConfigReturnsWorkspaceLayoutAndDesks() {
        Long wid = createWorkspace(rest, 1L).id();

        ResponseEntity<SessionConfigResponse> r = rest.exchange(base(wid) + "/session-config",
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), SessionConfigResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().workspaceId()).isEqualTo(wid);
        assertThat(r.getBody().layout()).isNotNull();
        assertThat(r.getBody().desks()).extracting("userId").contains(1L); // the owner
    }

    @Test
    void joinValidationAndMemberRole() {
        Long wid = createWorkspace(rest, 2L).id();

        ResponseEntity<JoinValidationResponse> join = rest.exchange(base(wid) + "/join-validation/2",
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), JoinValidationResponse.class);
        assertThat(join.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(join.getBody().allowed()).isTrue();
        assertThat(join.getBody().role()).isEqualTo(WorkspaceRole.OWNER);

        ResponseEntity<MemberRoleResponse> role = rest.exchange(base(wid) + "/members/2/role",
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), MemberRoleResponse.class);
        assertThat(role.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(role.getBody().role()).isEqualTo(WorkspaceRole.OWNER);

        // non-member -> 404
        ResponseEntity<String> missing = rest.exchange(base(wid) + "/members/9999/role",
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void presenceSyncUpdatesDesk() {
        Long wid = createWorkspace(rest, 3L).id();

        ResponseEntity<Void> presence = rest.exchange(base(wid) + "/presence", HttpMethod.POST,
                new HttpEntity<>(new PresenceSyncRequest(3L, true, DeskStatus.AWAY, null, 42, 24), internalHeaders()),
                Void.class);
        assertThat(presence.getStatusCode()).isEqualTo(HttpStatus.OK);

        SessionConfigResponse cfg = rest.exchange(base(wid) + "/session-config", HttpMethod.GET,
                new HttpEntity<>(internalHeaders()), SessionConfigResponse.class).getBody();
        var ownerDesk = cfg.desks().stream().filter(d -> d.userId().equals(3L)).findFirst().orElseThrow();
        assertThat(ownerDesk.isOnline()).isTrue();
        assertThat(ownerDesk.positionX()).isEqualTo(42);
    }

    @Test
    void chatContextReturnsChannelKey() {
        Long wid = createWorkspace(rest, 4L).id();
        ResponseEntity<ChatContextResponse> r = rest.exchange(base(wid) + "/chat-context",
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), ChatContextResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().channelKey()).isEqualTo("workspace:" + wid);
    }

    @Test
    void internalEndpointsRejectMissingToken() {
        Long wid = createWorkspace(rest, 5L).id();
        ResponseEntity<String> r = rest.exchange(base(wid) + "/session-config", HttpMethod.GET,
                new HttpEntity<>(null), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
