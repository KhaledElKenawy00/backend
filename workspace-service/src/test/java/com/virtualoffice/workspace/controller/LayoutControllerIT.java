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
import com.virtualoffice.workspace.dto.LayoutLayer;
import com.virtualoffice.workspace.dto.LayoutZone;
import com.virtualoffice.workspace.dto.request.UpdateLayoutRequest;
import com.virtualoffice.workspace.dto.response.LayoutResponse;
import com.virtualoffice.workspace.model.enums.ZoneType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LayoutControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private UpdateLayoutRequest layout(long expectedVersion) {
        // voiceRoomId is globally unique, so generate a fresh one per call (tests share one DB)
        String voiceRoomId = uniqueSlug("voice");
        return new UpdateLayoutRequest(expectedVersion, 32, 80, 60, "{\"bg\":\"day\"}",
                List.of(),
                List.of(new LayoutLayer("Ground", 0, false, "[[1,2],[3,4]]"),
                        new LayoutLayer("Walls", 1, true, "[[0,0],[0,0]]")),
                List.of(new LayoutZone(ZoneType.MEETING_ROOM, "Sync Room", 5, 5, 10, 10, voiceRoomId, null)),
                List.of());
    }

    @Test
    void defaultLayoutIsEmptyThenUpdatable() {
        Long wid = createWorkspace(rest, 1L).id();

        ResponseEntity<LayoutResponse> initial = rest.exchange("/api/workspace/" + wid + "/layout",
                HttpMethod.GET, new HttpEntity<>(userHeaders(1L)), LayoutResponse.class);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getBody().layoutVersion()).isZero();
        assertThat(initial.getBody().layers()).isEmpty();

        ResponseEntity<LayoutResponse> updated = rest.exchange("/api/workspace/" + wid + "/layout",
                HttpMethod.PUT, new HttpEntity<>(layout(0L), userHeaders(1L)), LayoutResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().layoutVersion()).isEqualTo(1L);
        assertThat(updated.getBody().layers()).hasSize(2);
        assertThat(updated.getBody().zones()).hasSize(1);
        assertThat(updated.getBody().zones().get(0).voiceRoomId()).isNotBlank();

        // a re-read reflects the persisted layout
        ResponseEntity<LayoutResponse> reread = rest.exchange("/api/workspace/" + wid + "/layout",
                HttpMethod.GET, new HttpEntity<>(userHeaders(1L)), LayoutResponse.class);
        assertThat(reread.getBody().layers()).hasSize(2);
    }

    @Test
    void staleVersionReturns409() {
        Long wid = createWorkspace(rest, 2L).id();
        // first update bumps version to 1
        assertThat(rest.exchange("/api/workspace/" + wid + "/layout", HttpMethod.PUT,
                new HttpEntity<>(layout(0L), userHeaders(2L)), LayoutResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // second update still claims version 0 -> conflict
        ResponseEntity<String> stale = rest.exchange("/api/workspace/" + wid + "/layout", HttpMethod.PUT,
                new HttpEntity<>(layout(0L), userHeaders(2L)), String.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nonAdminCannotEditLayout() {
        Long wid = createWorkspace(rest, 3L).id();
        ResponseEntity<String> r = rest.exchange("/api/workspace/" + wid + "/layout", HttpMethod.PUT,
                new HttpEntity<>(layout(0L), userHeaders(999L)), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
