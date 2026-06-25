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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class HealthEndpointIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void healthIsOpenAndReturnsOk() {
        ResponseEntity<String> r = rest.getForEntity("/api/workspace/health", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo("OK");
    }

    @Test
    void internalEndpointIsBlockedWithoutToken() {
        ResponseEntity<String> r = rest.getForEntity("/api/internal/anything", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void internalEndpointPassesFilterWithToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", "test-internal-token");
        ResponseEntity<String> r = rest.exchange(
                "/api/internal/anything", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class);
        // the correct token clears the internal-auth filter; downstream there is no such
        // route yet, so it must NOT be a 403 from the filter (it falls through to routing)
        assertThat(r.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
