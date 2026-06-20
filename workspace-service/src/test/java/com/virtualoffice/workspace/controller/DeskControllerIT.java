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

import com.fasterxml.jackson.databind.JsonNode;
import com.virtualoffice.workspace.AbstractIntegrationTest;
import com.virtualoffice.workspace.Fixtures;
import com.virtualoffice.workspace.dto.request.UpdateDeskRequest;
import com.virtualoffice.workspace.dto.request.UpdateStatusRequest;
import com.virtualoffice.workspace.dto.response.DeskResponse;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.enums.DeskStatus;
import com.virtualoffice.workspace.model.enums.WorkspaceRole;
import com.virtualoffice.workspace.repository.DeskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeskControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    DeskRepository deskRepository;

    private Desk seedMember(Long workspaceId, long userId, boolean active) {
        return deskRepository.save(Fixtures.desk(workspaceId, userId).isActive(active).build());
    }

    @Test
    void ownerCanReadOwnDesk() {
        Long wid = createWorkspace(rest, 1L).id();
        ResponseEntity<DeskResponse> r = rest.exchange("/api/workspace/" + wid + "/desks/me",
                HttpMethod.GET, new HttpEntity<>(userHeaders(1L)), DeskResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().role()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    void nonMemberGetsForbiddenOnMe() {
        Long wid = createWorkspace(rest, 2L).id();
        ResponseEntity<String> r = rest.exchange("/api/workspace/" + wid + "/desks/me",
                HttpMethod.GET, new HttpEntity<>(userHeaders(98765L)), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void directoryExcludesInactiveMembers() {
        Long wid = createWorkspace(rest, 3L).id();
        seedMember(wid, 31L, true);
        seedMember(wid, 32L, false); // inactive — must be excluded

        ResponseEntity<JsonNode> r = rest.exchange("/api/workspace/" + wid + "/desks",
                HttpMethod.GET, new HttpEntity<>(userHeaders(3L)), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode content = r.getBody().get("content");
        List<Long> userIds = content.findValuesAsText("userId").stream().map(Long::valueOf).toList();
        assertThat(userIds).contains(3L, 31L).doesNotContain(32L);
    }

    @Test
    void memberCanUpdateOwnDeskButNotOthers() {
        Long wid = createWorkspace(rest, 4L).id();
        Long deskId = seedMember(wid, 41L, true).getId();

        ResponseEntity<DeskResponse> ok = rest.exchange("/api/workspace/" + wid + "/desks/" + deskId,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateDeskRequest("Forty One", "41", "Engineer", "my bio",
                        com.virtualoffice.workspace.model.enums.AvatarCharacter.LUCY, "Africa/Cairo", null,
                        List.of("https://example.com/a"),
                        List.of(new UpdateDeskRequest.WidgetInput("CLOCK", "Clock", 0, "{\"tz\":\"UTC\"}"))),
                        userHeaders(41L)),
                DeskResponse.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().fullName()).isEqualTo("Forty One");
        assertThat(ok.getBody().nickName()).isEqualTo("41");
        assertThat(ok.getBody().links()).containsExactly("https://example.com/a");
        assertThat(ok.getBody().widgets()).hasSize(1);
        assertThat(ok.getBody().widgets().get(0).type()).isEqualTo("CLOCK");

        ResponseEntity<String> forbidden = rest.exchange("/api/workspace/" + wid + "/desks/" + deskId,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateDeskRequest("Hacker", null, null, null, null, null, null, null, null),
                        userHeaders(4L)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void memberCanReadAnotherDeskById() {
        Long wid = createWorkspace(rest, 8L).id();
        Long deskId = seedMember(wid, 81L, true).getId();

        ResponseEntity<DeskResponse> r = rest.exchange("/api/workspace/" + wid + "/desks/" + deskId,
                HttpMethod.GET, new HttpEntity<>(userHeaders(8L)), DeskResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().userId()).isEqualTo(81L);

        // a non-member is rejected
        ResponseEntity<String> forbidden = rest.exchange("/api/workspace/" + wid + "/desks/" + deskId,
                HttpMethod.GET, new HttpEntity<>(userHeaders(70000L)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void memberCanPatchOwnStatus() {
        Long wid = createWorkspace(rest, 5L).id();
        Long deskId = seedMember(wid, 51L, true).getId();

        ResponseEntity<DeskResponse> r = rest.exchange("/api/workspace/" + wid + "/desks/" + deskId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(new UpdateStatusRequest(DeskStatus.FOCUS_MODE, null, "deep work"), userHeaders(51L)),
                DeskResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().status()).isEqualTo(DeskStatus.FOCUS_MODE);
    }

    @Test
    void adminRemovesMemberButNotOwner() {
        Long wid = createWorkspace(rest, 6L).id();
        Long memberDeskId = seedMember(wid, 61L, true).getId();

        ResponseEntity<Void> removed = rest.exchange("/api/workspace/" + wid + "/desks/" + memberDeskId,
                HttpMethod.DELETE, new HttpEntity<>(userHeaders(6L)), Void.class);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // owner's own desk cannot be removed
        Long ownerDeskId = deskRepository.findByWorkspaceIdAndUserId(wid, 6L).orElseThrow().getId();
        ResponseEntity<String> conflict = rest.exchange("/api/workspace/" + wid + "/desks/" + ownerDeskId,
                HttpMethod.DELETE, new HttpEntity<>(userHeaders(6L)), String.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
