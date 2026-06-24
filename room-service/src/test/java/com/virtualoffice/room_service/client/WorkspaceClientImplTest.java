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
package com.virtualoffice.room_service.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * Tests the abstract internal-comms layer (INTEGRATION.md §4.1) against a mock HTTP endpoint:
 * verifies the exact request workspace-service receives (method, URL, {@code X-Internal-Token})
 * and that each response (200 / 404 / 5xx) maps to the right outcome.
 */
class WorkspaceClientImplTest {

    private static final String BASE_URL = "http://workspace-service:8087";
    private static final String TOKEN = "secret-token";
    private static final String ROLE_URL = BASE_URL + "/api/internal/workspace/1/members/2/role";

    /** Builds a client whose RestClient is intercepted by a freshly-stubbed mock server. */
    private WorkspaceClient clientStubbedWith(Consumer<MockRestServiceServer> stub) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        stub.accept(server);
        return new WorkspaceClientImpl(builder, BASE_URL, TOKEN);
    }

    @Test
    void getMemberRoleSendsInternalTokenAndParsesRole() {
        WorkspaceClient client = clientStubbedWith(server -> server
                .expect(requestTo(ROLE_URL))
                .andExpect(method(GET))
                .andExpect(header(WorkspaceClientImpl.HEADER_INTERNAL_TOKEN, TOKEN))
                .andRespond(withSuccess(
                        "{\"userId\":2,\"workspaceId\":1,\"role\":\"ADMIN\",\"isActive\":true}",
                        MediaType.APPLICATION_JSON)));

        Optional<WorkspaceMemberRole> role = client.getMemberRole(1, 2);

        assertThat(role).isPresent();
        assertThat(role.get().role()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(role.get().active()).isTrue();
        assertThat(role.get().workspaceId()).isEqualTo(1);
    }

    @Test
    void getMemberRoleReturnsEmptyOnNotFound() {
        WorkspaceClient client = clientStubbedWith(server -> server
                .expect(requestTo(ROLE_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)));

        assertThat(client.getMemberRole(1, 2)).isEmpty();
    }

    @Test
    void getMemberRoleFailsClosedWhenWorkspaceServiceErrors() {
        WorkspaceClient client = clientStubbedWith(server -> server
                .expect(requestTo(ROLE_URL))
                .andRespond(withServerError()));

        assertThatThrownBy(() -> client.getMemberRole(1, 2))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void requireRolePassesWhenMemberMeetsMinimum() {
        WorkspaceClient client = clientStubbedWith(server -> server
                .expect(requestTo(ROLE_URL))
                .andRespond(withSuccess(
                        "{\"userId\":2,\"workspaceId\":1,\"role\":\"MEMBER\",\"isActive\":true}",
                        MediaType.APPLICATION_JSON)));

        client.requireRole(1, 2, WorkspaceRole.MEMBER); // does not throw
    }

    @Test
    void requireRoleForbidsWhenRoleTooLow() {
        WorkspaceClient client = clientStubbedWith(server -> server
                .expect(requestTo(ROLE_URL))
                .andRespond(withSuccess(
                        "{\"userId\":2,\"workspaceId\":1,\"role\":\"MEMBER\",\"isActive\":true}",
                        MediaType.APPLICATION_JSON)));

        assertForbidden(() -> client.requireRole(1, 2, WorkspaceRole.ADMIN));
    }

    @Test
    void requireRoleForbidsWhenNotAMember() {
        WorkspaceClient client = clientStubbedWith(server -> server
                .expect(requestTo(ROLE_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)));

        assertForbidden(() -> client.requireRole(1, 2, WorkspaceRole.MEMBER));
    }

    @Test
    void requireRoleForbidsWhenDeskInactive() {
        WorkspaceClient client = clientStubbedWith(server -> server
                .expect(requestTo(ROLE_URL))
                .andRespond(withSuccess(
                        "{\"userId\":2,\"workspaceId\":1,\"role\":\"ADMIN\",\"isActive\":false}",
                        MediaType.APPLICATION_JSON)));

        assertForbidden(() -> client.requireRole(1, 2, WorkspaceRole.MEMBER));
    }

    private static void assertForbidden(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
