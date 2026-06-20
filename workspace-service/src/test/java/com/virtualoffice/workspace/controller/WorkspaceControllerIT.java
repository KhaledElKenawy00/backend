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
import com.virtualoffice.workspace.dto.request.CreateWorkspaceRequest;
import com.virtualoffice.workspace.dto.request.UpdateWorkspaceRequest;
import com.virtualoffice.workspace.dto.response.WorkspaceResponse;
import com.virtualoffice.workspace.model.enums.WorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private ResponseEntity<WorkspaceResponse> create(long userId, String slug) {
        var body = new CreateWorkspaceRequest("Acme " + slug, slug, "desc", null, "UTC");
        return rest.exchange("/api/workspace", HttpMethod.POST,
                new HttpEntity<>(body, userHeaders(userId)), WorkspaceResponse.class);
    }

    @Test
    void createReturns201WithOwnerDesk() {
        ResponseEntity<WorkspaceResponse> r = create(1L, uniqueSlug("acme"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().ownerId()).isEqualTo(1L);
        assertThat(r.getBody().status()).isEqualTo(WorkspaceStatus.ACTIVE);

        // creator is now a member: can read it
        ResponseEntity<WorkspaceResponse> got = rest.exchange("/api/workspace/" + r.getBody().id(),
                HttpMethod.GET, new HttpEntity<>(userHeaders(1L)), WorkspaceResponse.class);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Canonical persistence round-trip: create -> GET (stored?) -> update -> GET (updated?).
     * The proof is the SEPARATE GET after each mutation — re-reading from the DB rather than
     * trusting the create/update response body (which would pass even if nothing were saved).
     */
    @Test
    void persistsAcrossCreateUpdateAndGet() {
        // 1. create
        WorkspaceResponse created = create(1L, uniqueSlug("rt")).getBody();
        assertThat(created).isNotNull();
        Long id = created.id();

        // 2. GET it back — proves the create was actually stored
        WorkspaceResponse afterCreate = rest.exchange("/api/workspace/" + id, HttpMethod.GET,
                new HttpEntity<>(userHeaders(1L)), WorkspaceResponse.class).getBody();
        assertThat(afterCreate).isNotNull();
        assertThat(afterCreate.id()).isEqualTo(id);
        assertThat(afterCreate.description()).isEqualTo("desc");

        // 3. update
        ResponseEntity<WorkspaceResponse> updated = rest.exchange("/api/workspace/" + id, HttpMethod.PUT,
                new HttpEntity<>(new UpdateWorkspaceRequest("Renamed Co", "new description", null, null),
                        userHeaders(1L)),
                WorkspaceResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4. GET again — proves the update was persisted, not just echoed back
        WorkspaceResponse afterUpdate = rest.exchange("/api/workspace/" + id, HttpMethod.GET,
                new HttpEntity<>(userHeaders(1L)), WorkspaceResponse.class).getBody();
        assertThat(afterUpdate).isNotNull();
        assertThat(afterUpdate.name()).isEqualTo("Renamed Co");
        assertThat(afterUpdate.description()).isEqualTo("new description");
    }

    @Test
    void duplicateSlugReturns409() {
        String slug = uniqueSlug("dup");
        assertThat(create(1L, slug).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> second = rest.exchange("/api/workspace", HttpMethod.POST,
                new HttpEntity<>(new CreateWorkspaceRequest("X", slug, null, null, "UTC"), userHeaders(2L)),
                String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nonMemberCannotReadWorkspace() {
        Long id = create(1L, uniqueSlug("priv")).getBody().id();
        ResponseEntity<String> r = rest.exchange("/api/workspace/" + id, HttpMethod.GET,
                new HttpEntity<>(userHeaders(999L)), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidSlugReturns400() {
        ResponseEntity<String> r = rest.exchange("/api/workspace", HttpMethod.POST,
                new HttpEntity<>(new CreateWorkspaceRequest("Bad", "Has Spaces!", null, null, "UTC"), userHeaders(1L)),
                String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ownerCanUpdateNonMemberCannot() {
        Long id = create(3L, uniqueSlug("upd")).getBody().id();

        ResponseEntity<WorkspaceResponse> ok = rest.exchange("/api/workspace/" + id, HttpMethod.PUT,
                new HttpEntity<>(new UpdateWorkspaceRequest("Renamed", null, null, null), userHeaders(3L)),
                WorkspaceResponse.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().name()).isEqualTo("Renamed");

        ResponseEntity<String> forbidden = rest.exchange("/api/workspace/" + id, HttpMethod.PUT,
                new HttpEntity<>(new UpdateWorkspaceRequest("Hack", null, null, null), userHeaders(404L)),
                String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerCanArchive() {
        Long id = create(7L, uniqueSlug("arch")).getBody().id();
        ResponseEntity<WorkspaceResponse> r = rest.exchange("/api/workspace/" + id, HttpMethod.DELETE,
                new HttpEntity<>(userHeaders(7L)), WorkspaceResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().status()).isEqualTo(WorkspaceStatus.ARCHIVED);
    }

    @Test
    void rotateInviteTokenChangesToken() {
        WorkspaceResponse created = create(8L, uniqueSlug("rot")).getBody();
        UUID before = created.inviteToken();
        ResponseEntity<WorkspaceResponse> r = rest.exchange(
                "/api/workspace/" + created.id() + "/rotate-invite-token", HttpMethod.POST,
                new HttpEntity<>(userHeaders(8L)), WorkspaceResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().inviteToken()).isNotEqualTo(before);
    }

    @Test
    void mineListsWorkspacesUserBelongsTo() {
        Long id = create(55L, uniqueSlug("mine")).getBody().id();
        ResponseEntity<WorkspaceResponse[]> r = rest.exchange("/api/workspace/mine", HttpMethod.GET,
                new HttpEntity<>(userHeaders(55L)), WorkspaceResponse[].class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).extracting(WorkspaceResponse::id).contains(id);
    }

    @Test
    void missingUserIdHeaderReturns401() {
        ResponseEntity<String> r = rest.exchange("/api/workspace/mine", HttpMethod.GET,
                new HttpEntity<>(null), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
