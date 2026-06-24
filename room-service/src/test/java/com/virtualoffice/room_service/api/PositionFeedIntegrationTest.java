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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the SkyOffice → room-service position feed (INTEGRATION.md §4.2/§4.3) over
 * the real application context (Testcontainers via {@link AbstractRoomIntegrationTest}). Verifies
 * the {@code /api/internal/**} guard ({@code X-Internal-Token}) and that a posted snapshot is turned
 * into voice-channel assignments. {@link WorkspaceClient} is mocked to supply zones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PositionFeedIntegrationTest extends AbstractRoomIntegrationTest {

    private static final String INTERNAL_TOKEN = "dev-internal-token"; // application.yml default
    private static final String POSITIONS_URL = "/api/internal/workspace/1/positions";

    @LocalServerPort
    private int port;

    @MockitoBean
    private WorkspaceClient workspaceClient;

    private RestClient http() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void rejectsPositionFeedWithoutInternalToken() {
        ResponseEntity<Void> response = http().post().uri(POSITIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("positions", List.of(Map.of("userId", 10, "x", 0, "y", 0))))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).<Void>body(null), false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void computesVoiceGroupsFromPostedPositions() {
        Mockito.when(workspaceClient.getZones(1)).thenReturn(List.of()); // open world, default radius

        ResponseEntity<Map<String, String>> response = http().post().uri(POSITIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .body(Map.of("positions", List.of(
                        Map.of("userId", 10, "x", 0, "y", 0),
                        Map.of("userId", 20, "x", 30, "y", 0),
                        Map.of("userId", 30, "x", 9000, "y", 9000))))
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    Map<String, String> body = status.is2xxSuccessful()
                            ? res.bodyTo(new ParameterizedTypeReference<Map<String, String>>() {
                            })
                            : null;
                    return ResponseEntity.status(status).body(body);
                }, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 10 and 20 are within the default radius -> same ad-hoc channel; 30 is alone -> no channel.
        assertThat(response.getBody()).containsEntry("10", "prox-1-10").containsEntry("20", "prox-1-10");
        assertThat(response.getBody()).doesNotContainKey("30");
    }
}
