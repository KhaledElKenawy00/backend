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
import com.virtualoffice.workspace.dto.request.CreateMapObjectRequest;
import com.virtualoffice.workspace.dto.response.MapObjectResponse;
import com.virtualoffice.workspace.model.enums.MapObjectType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class MapObjectControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private ResponseEntity<MapObjectResponse> create(Long wid, long userId, MapObjectType type) {
        return rest.exchange("/api/workspace/" + wid + "/map-objects", HttpMethod.POST,
                new HttpEntity<>(new CreateMapObjectRequest(type, "obj", 1, 2, 3), userHeaders(userId)),
                MapObjectResponse.class);
    }

    @Test
    void adminCreatesWithRoomIdNonAdminForbidden() {
        Long wid = createWorkspace(rest, 1L).id();

        ResponseEntity<MapObjectResponse> created = create(wid, 1L, MapObjectType.COMPUTER);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().roomId()).isNotBlank();

        ResponseEntity<String> forbidden = rest.exchange("/api/workspace/" + wid + "/map-objects", HttpMethod.POST,
                new HttpEntity<>(new CreateMapObjectRequest(MapObjectType.COMPUTER, "x", 1, 1, 1), userHeaders(999L)),
                String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listExcludesInactiveAfterToggle() {
        Long wid = createWorkspace(rest, 2L).id();
        Long id = create(wid, 2L, MapObjectType.WHITEBOARD).getBody().id();

        rest.exchange("/api/workspace/" + wid + "/map-objects/" + id + "/toggle", HttpMethod.PATCH,
                new HttpEntity<>(userHeaders(2L)), MapObjectResponse.class);

        ResponseEntity<MapObjectResponse[]> list = rest.exchange("/api/workspace/" + wid + "/map-objects",
                HttpMethod.GET, new HttpEntity<>(userHeaders(2L)), MapObjectResponse[].class);
        assertThat(list.getBody()).extracting(MapObjectResponse::id).doesNotContain(id);
    }

    @Test
    void deleteRemovesObject() {
        Long wid = createWorkspace(rest, 3L).id();
        Long id = create(wid, 3L, MapObjectType.COMPUTER).getBody().id();

        ResponseEntity<Void> del = rest.exchange("/api/workspace/" + wid + "/map-objects/" + id,
                HttpMethod.DELETE, new HttpEntity<>(userHeaders(3L)), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
