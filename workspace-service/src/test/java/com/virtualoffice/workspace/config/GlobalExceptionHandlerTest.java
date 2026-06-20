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
package com.virtualoffice.workspace.config;

import com.virtualoffice.workspace.exception.ConflictException;
import com.virtualoffice.workspace.exception.ForbiddenException;
import com.virtualoffice.workspace.exception.GoneException;
import com.virtualoffice.workspace.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundMapsTo404() {
        ResponseEntity<Map<String, Object>> r = handler.handleNotFound(new ResourceNotFoundException("nope"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody()).containsEntry("status", 404).containsEntry("message", "nope");
    }

    @Test
    void forbiddenMapsTo403() {
        assertThat(handler.handleForbidden(new ForbiddenException("x")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void conflictMapsTo409() {
        assertThat(handler.handleConflict(new ConflictException("dup")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void goneMapsTo410() {
        assertThat(handler.handleGone(new GoneException("expired")).getStatusCode())
                .isEqualTo(HttpStatus.GONE);
    }

    @Test
    void dataIntegrityMapsTo409() {
        assertThat(handler.handleDataIntegrity(new DataIntegrityViolationException("dup key")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void optimisticLockMapsTo409() {
        assertThat(handler.handleOptimisticLock(new OptimisticLockingFailureException("stale")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void genericMapsTo500WithSafeMessage() {
        ResponseEntity<Map<String, Object>> r = handler.handleGeneric(new RuntimeException("leak"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // internal detail must not leak to the client
        assertThat(r.getBody()).containsEntry("message", "an unexpected error occurred");
    }
}
