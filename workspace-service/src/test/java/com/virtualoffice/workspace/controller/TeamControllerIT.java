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
import com.virtualoffice.workspace.dto.request.CreateTeamRequest;
import com.virtualoffice.workspace.dto.request.UpdateTeamRequest;
import com.virtualoffice.workspace.dto.response.TeamResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class TeamControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private ResponseEntity<TeamResponse> createTeam(long workspaceId, long userId, String name) {
        return rest.exchange("/api/workspace/" + workspaceId + "/teams", HttpMethod.POST,
                new HttpEntity<>(new CreateTeamRequest(name, "desc"), userHeaders(userId)), TeamResponse.class);
    }

    @Test
    void adminCreatesTeamNonMemberForbidden() {
        Long wid = createWorkspace(rest, 1L).id();

        ResponseEntity<TeamResponse> created = createTeam(wid, 1L, "Engineering");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().name()).isEqualTo("Engineering");

        ResponseEntity<String> forbidden = rest.exchange("/api/workspace/" + wid + "/teams", HttpMethod.POST,
                new HttpEntity<>(new CreateTeamRequest("Sales", null), userHeaders(999L)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateTeamNameReturns409() {
        Long wid = createWorkspace(rest, 2L).id();
        assertThat(createTeam(wid, 2L, "Design").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createTeam(wid, 2L, "Design").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listReturnsTeamsForMembers() {
        Long wid = createWorkspace(rest, 3L).id();
        createTeam(wid, 3L, "Eng");
        createTeam(wid, 3L, "Ops");

        ResponseEntity<TeamResponse[]> r = rest.exchange("/api/workspace/" + wid + "/teams", HttpMethod.GET,
                new HttpEntity<>(userHeaders(3L)), TeamResponse[].class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).extracting(TeamResponse::name).contains("Eng", "Ops");
    }

    @Test
    void updateAndDeleteTeam() {
        Long wid = createWorkspace(rest, 4L).id();
        Long teamId = createTeam(wid, 4L, "Eng").getBody().id();

        ResponseEntity<TeamResponse> updated = rest.exchange(
                "/api/workspace/" + wid + "/teams/" + teamId, HttpMethod.PUT,
                new HttpEntity<>(new UpdateTeamRequest("Platform", null), userHeaders(4L)), TeamResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().name()).isEqualTo("Platform");

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/workspace/" + wid + "/teams/" + teamId, HttpMethod.DELETE,
                new HttpEntity<>(userHeaders(4L)), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void updateMissingTeamReturns404() {
        Long wid = createWorkspace(rest, 6L).id();
        ResponseEntity<String> r = rest.exchange(
                "/api/workspace/" + wid + "/teams/999999", HttpMethod.PUT,
                new HttpEntity<>(new UpdateTeamRequest("X", null), userHeaders(6L)), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
