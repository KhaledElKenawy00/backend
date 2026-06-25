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
package com.virtualoffice.room_service.api;

import com.virtualoffice.room_service.AbstractRoomIntegrationTest;
import com.virtualoffice.room_service.client.WorkspaceClient;
import com.virtualoffice.room_service.client.WorkspaceRole;
import com.virtualoffice.room_service.dto.response.JoinRoomResponse;
import com.virtualoffice.room_service.dto.response.PaginatedResponse;
import com.virtualoffice.room_service.dto.response.ParticipantResponse;
import com.virtualoffice.room_service.dto.response.RoomResponse;
import com.virtualoffice.room_service.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * Frontend-workflow integration test: drives the room-service REST API end to end as the frontend
 * would — over real Mongo/RabbitMQ/Redis (Testcontainers, {@link AbstractRoomIntegrationTest}) with
 * gateway {@code X-User-Id}/{@code X-User-Role} headers. {@link WorkspaceClient} (the cross-service
 * authz layer) is mocked so the test is hermetic; its real HTTP contract is covered by
 * {@code WorkspaceClientImplTest}.
 *
 * <p>Covers a full lifecycle (create → get → update → list → join → participants → leave → delete →
 * get) and the security boundary (missing identity, workspace-role denial on create and join).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomApiWorkflowIntegrationTest extends AbstractRoomIntegrationTest {

    private static final int ALICE = 10;    // workspace member, room creator
    private static final int MALLORY = 99;  // not a workspace member
    private static final int WORKSPACE = 1;

    @LocalServerPort
    private int port;

    @Autowired
    private RoomRepository roomRepository;

    /** The cross-service authz layer — mocked; default is "allowed" (no-op requireRole). */
    @MockitoBean
    private WorkspaceClient workspaceClient;

    private RestClient http;

    @BeforeEach
    void setUp() {
        roomRepository.deleteAll();
        http = RestClient.create("http://localhost:" + port);
    }

    @Test
    void fullRoomLifecycle() {
        // create
        ResponseEntity<RoomResponse> created = call(HttpMethod.POST, "/api/rooms", ALICE,
                Map.of("name", "Standup", "workspaceId", WORKSPACE, "members", List.of(ALICE)), RoomResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String roomId = created.getBody().getId();
        assertThat(roomId).isNotBlank();
        assertThat(created.getBody().getAgoraChannelName()).startsWith("room-");

        // get
        RoomResponse fetched = call(HttpMethod.GET, "/api/rooms/" + roomId, ALICE, null, RoomResponse.class).getBody();
        assertThat(fetched.getName()).isEqualTo("Standup");

        // update
        RoomResponse updated = call(HttpMethod.PATCH, "/api/rooms/" + roomId, ALICE,
                Map.of("name", "Daily Standup"), RoomResponse.class).getBody();
        assertThat(updated.getName()).isEqualTo("Daily Standup");

        // list -> contains the room
        PaginatedResponse<RoomResponse> list = http.get().uri("/api/rooms?workspaceId=" + WORKSPACE)
                .header("X-User-Id", String.valueOf(ALICE)).header("X-User-Role", "USER")
                .retrieve().body(new ParameterizedTypeReference<PaginatedResponse<RoomResponse>>() {
                });
        assertThat(list.getContent()).extracting(RoomResponse::getId).contains(roomId);

        // join -> presence + agora token; participant list includes the joiner
        JoinRoomResponse joined = call(HttpMethod.POST, "/api/rooms/" + roomId + "/join", ALICE, null,
                JoinRoomResponse.class).getBody();
        assertThat(joined.getAgoraToken()).isNotBlank();
        assertThat(joined.getParticipants()).extracting(ParticipantResponse::getUserId).contains(ALICE);

        // participants endpoint reflects the same
        ResponseEntity<List<ParticipantResponse>> participants = http.get()
                .uri("/api/rooms/" + roomId + "/participants")
                .header("X-User-Id", String.valueOf(ALICE)).header("X-User-Role", "USER")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode())
                        .body(res.bodyTo(new ParameterizedTypeReference<List<ParticipantResponse>>() {
                        })), false);
        assertThat(participants.getBody()).extracting(ParticipantResponse::getUserId).contains(ALICE);

        // leave
        ResponseEntity<Void> left = call(HttpMethod.POST, "/api/rooms/" + roomId + "/leave", ALICE, null, Void.class);
        assertThat(left.getStatusCode().is2xxSuccessful()).isTrue();

        // delete
        ResponseEntity<Void> deleted = call(HttpMethod.DELETE, "/api/rooms/" + roomId, ALICE, null, Void.class);
        assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();

        // get -> gone
        ResponseEntity<String> afterDelete = call(HttpMethod.GET, "/api/rooms/" + roomId, ALICE, null, String.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsRequestsWithoutIdentityHeader() {
        ResponseEntity<Void> response = http.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Role", "USER") // X-User-Id deliberately absent
                .body(Map.of("name", "x", "workspaceId", WORKSPACE))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).<Void>body(null), false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsRoomCreationWhenNotAuthorizedInWorkspace() {
        doThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "not a member"))
                .when(workspaceClient).requireRole(eq(WORKSPACE), eq(MALLORY), eq(WorkspaceRole.MEMBER));

        ResponseEntity<String> response = call(HttpMethod.POST, "/api/rooms", MALLORY,
                Map.of("name", "secret", "workspaceId", WORKSPACE), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(roomRepository.count()).isZero();
    }

    @Test
    void nonWorkspaceMemberCannotJoinRoom() {
        String roomId = call(HttpMethod.POST, "/api/rooms", ALICE,
                Map.of("name", "Standup", "workspaceId", WORKSPACE, "members", List.of(ALICE)),
                RoomResponse.class).getBody().getId();

        // Mallory was removed from the workspace: the role check fails on join.
        doThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "not a member"))
                .when(workspaceClient).requireRole(eq(WORKSPACE), eq(MALLORY), eq(WorkspaceRole.MEMBER));

        ResponseEntity<String> join = call(HttpMethod.POST, "/api/rooms/" + roomId + "/join", MALLORY, null,
                String.class);
        assertThat(join.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Issues a request with the gateway-style identity headers; never throws on 4xx/5xx. */
    private <T> ResponseEntity<T> call(HttpMethod method, String path, Integer userId, Object body, Class<T> type) {
        RestClient.RequestBodySpec spec = http.method(method).uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Role", "USER");
        if (body != null) {
            spec.body(body);
        }
        return spec.exchange((req, res) -> {
            HttpStatusCode status = res.getStatusCode();
            T parsed = status.is2xxSuccessful() && type != Void.class ? res.bodyTo(type) : null;
            return ResponseEntity.status(status).body(parsed);
        }, false);
    }
}
