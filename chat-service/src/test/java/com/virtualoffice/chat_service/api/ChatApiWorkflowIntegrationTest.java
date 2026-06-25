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
package com.virtualoffice.chat_service.api;

import com.virtualoffice.chat_service.AbstractChatIntegrationTest;
import com.virtualoffice.chat_service.client.WorkspaceClient;
import com.virtualoffice.chat_service.client.WorkspaceRole;
import com.virtualoffice.chat_service.dto.response.ChannelResponse;
import com.virtualoffice.chat_service.dto.response.MessageResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import com.virtualoffice.chat_service.repository.MessageRepository;
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
 * Frontend-workflow integration test: drives the chat-service REST API end to end exactly as the
 * frontend would — over real Mongo/RabbitMQ/Redis (Testcontainers, {@link AbstractChatIntegrationTest})
 * with the gateway's {@code X-User-Id}/{@code X-User-Role} headers. {@link WorkspaceClient} (the
 * cross-service authz layer) is mocked so the test is hermetic; its real HTTP contract is covered by
 * {@code WorkspaceClientImplTest}.
 *
 * <p>Covers a full lifecycle (create → get → send → edit → get → delete → get) and the security
 * boundary (missing identity, non-membership, workspace-role denial).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatApiWorkflowIntegrationTest extends AbstractChatIntegrationTest {

    private static final int ALICE = 10;    // workspace member, channel creator
    private static final int MALLORY = 99;  // not a member of the channel
    private static final int WORKSPACE = 1;

    @LocalServerPort
    private int port;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private MessageRepository messageRepository;

    /** The cross-service authz layer — mocked; default is "allowed" (no-op requireRole). */
    @MockitoBean
    private WorkspaceClient workspaceClient;

    private RestClient http;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        channelRepository.deleteAll();
        http = RestClient.create("http://localhost:" + port);
    }

    @Test
    void fullChannelAndMessageLifecycle() {
        // create
        ResponseEntity<ChannelResponse> created = call(HttpMethod.POST, "/api/chat/channels", ALICE,
                Map.of("name", "general", "workspaceId", WORKSPACE, "members", List.of(ALICE)), ChannelResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String channelId = created.getBody().getId();
        assertThat(channelId).isNotBlank();

        // get -> reflects the created channel
        ChannelResponse fetched = call(HttpMethod.GET, "/api/chat/channels/" + channelId, ALICE, null,
                ChannelResponse.class).getBody();
        assertThat(fetched.getName()).isEqualTo("general");
        assertThat(fetched.getMembers()).contains(ALICE);

        // send a message
        ResponseEntity<MessageResponse> sent = call(HttpMethod.POST,
                "/api/chat/channels/" + channelId + "/messages", ALICE, Map.of("content", "hello world"),
                MessageResponse.class);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String messageId = sent.getBody().getId();
        assertThat(sent.getBody().getContent()).isEqualTo("hello world");

        // update (edit) the message
        MessageResponse edited = call(HttpMethod.PUT, "/api/chat/messages/" + messageId, ALICE,
                Map.of("content", "hello, edited"), MessageResponse.class).getBody();
        assertThat(edited.getContent()).isEqualTo("hello, edited");

        // get -> the edited content is visible, not deleted
        MessageResponse listed = onlyMessage(channelId);
        assertThat(listed.getContent()).isEqualTo("hello, edited");
        assertThat(listed.getDeleted()).isFalse();

        // delete
        ResponseEntity<Void> deleted = call(HttpMethod.DELETE, "/api/chat/messages/" + messageId, ALICE,
                null, Void.class);
        assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();

        // get -> the message is now soft-deleted
        assertThat(onlyMessage(channelId).getDeleted()).isTrue();
    }

    @Test
    void rejectsRequestsWithoutIdentityHeader() {
        // Body is valid so it passes @Valid; the request must fail on the missing identity, not validation.
        ResponseEntity<Void> response = http.post().uri("/api/chat/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Role", "USER") // X-User-Id deliberately absent
                .body(Map.of("name", "x", "workspaceId", WORKSPACE, "members", List.of(ALICE)))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).<Void>body(null), false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsChannelCreationWhenNotAuthorizedInWorkspace() {
        doThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "not a member"))
                .when(workspaceClient).requireRole(eq(WORKSPACE), eq(MALLORY), eq(WorkspaceRole.MEMBER));

        ResponseEntity<String> response = call(HttpMethod.POST, "/api/chat/channels", MALLORY,
                Map.of("name", "secret", "workspaceId", WORKSPACE, "members", List.of(MALLORY)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(channelRepository.count()).isZero();
    }

    @Test
    void nonMemberCannotSendOrReadChannelMessages() {
        String channelId = call(HttpMethod.POST, "/api/chat/channels", ALICE,
                Map.of("name", "general", "workspaceId", WORKSPACE, "members", List.of(ALICE)),
                ChannelResponse.class).getBody().getId();

        ResponseEntity<String> send = call(HttpMethod.POST, "/api/chat/channels/" + channelId + "/messages",
                MALLORY, Map.of("content", "let me in"), String.class);
        assertThat(send.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> read = call(HttpMethod.GET, "/api/chat/channels/" + channelId + "/messages",
                MALLORY, null, String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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

    private MessageResponse onlyMessage(String channelId) {
        PaginatedResponse<MessageResponse> page = http.get()
                .uri("/api/chat/channels/" + channelId + "/messages")
                .header("X-User-Id", String.valueOf(ALICE))
                .header("X-User-Role", "USER")
                .retrieve()
                .body(new ParameterizedTypeReference<PaginatedResponse<MessageResponse>>() {
                });
        assertThat(page.getContent()).hasSize(1);
        return page.getContent().get(0);
    }
}
