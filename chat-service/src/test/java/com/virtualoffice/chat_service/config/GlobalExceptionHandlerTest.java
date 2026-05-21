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
package com.virtualoffice.chat_service.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unchecked")
    private void assertErrorResponse(ResponseEntity<?> response, int status, String messageContains) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(status);
        assertThat(body.get("timestamp")).isNotNull();
        if (messageContains != null) {
            assertThat((String) body.get("message")).contains(messageContains);
        }
    }

    // ────────────────────────────────────────
    // mongo down
    // ────────────────────────────────────────

    @Nested
    class MongoFailure {

        @Test
        void shouldReturn503() {
            DataAccessResourceFailureException ex = new DataAccessResourceFailureException("mongo down");

            ResponseEntity<?> response = handler.handleMongoFailure(ex);

            assertErrorResponse(response, 503, "database temporarily unavailable");
        }
    }

    // ────────────────────────────────────────
    // redis down
    // ────────────────────────────────────────

    @Nested
    class RedisFailure {

        @Test
        void shouldReturn503() {
            RedisConnectionFailureException ex = new RedisConnectionFailureException("redis down");

            ResponseEntity<?> response = handler.handleRedisFailure(ex);

            assertErrorResponse(response, 503, "cache service temporarily unavailable");
        }
    }

    // ────────────────────────────────────────
    // @Valid failed
    // ────────────────────────────────────────

    @Nested
    class ValidationFailure {

        @Test
        void shouldReturn400WithFieldErrors() {
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            FieldError fieldError1 = new FieldError("obj", "name", "must not be blank");
            FieldError fieldError2 = new FieldError("obj", "content", "must not be null");
            when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

            ResponseEntity<?> response = handler.handleValidation(ex);

            assertErrorResponse(response, 400, "name: must not be blank");
        }

        @Test
        void shouldReturn400WithFallbackForNoFieldErrors() {
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(List.of());

            ResponseEntity<?> response = handler.handleValidation(ex);

            assertErrorResponse(response, 400, "validation failed");
        }
    }

    // ────────────────────────────────────────
    // bad ObjectId / illegal args
    // ────────────────────────────────────────

    @Nested
    class IllegalArgumentFailure {

        @Test
        void shouldReturn400() {
            IllegalArgumentException ex = new IllegalArgumentException("invalid hex string");

            ResponseEntity<?> response = handler.handleIllegalArgument(ex);

            assertErrorResponse(response, 400, "invalid hex string");
        }
    }

    // ────────────────────────────────────────
    // ResponseStatusException forwarding
    // ────────────────────────────────────────

    @Nested
    class ResponseStatusFailure {

        @Test
        void shouldForward404() {
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "channel not found");

            ResponseEntity<?> response = handler.handleResponseStatus(ex);

            assertErrorResponse(response, 404, "channel not found");
        }

        @Test
        void shouldForward403() {
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member");

            ResponseEntity<?> response = handler.handleResponseStatus(ex);

            assertErrorResponse(response, 403, "not a member");
        }

        @Test
        void shouldForward401() {
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing header");

            ResponseEntity<?> response = handler.handleResponseStatus(ex);

            assertErrorResponse(response, 401, "missing header");
        }
    }

    // ────────────────────────────────────────
    // catch-all
    // ────────────────────────────────────────

    @Nested
    class GenericFailure {

        @Test
        void shouldReturn500() {
            Exception ex = new RuntimeException("something bad happened");

            ResponseEntity<?> response = handler.handleGeneric(ex);

            assertErrorResponse(response, 500, "an unexpected error occurred");
        }

        @Test
        void shouldReturn500WithErrorPhrase() {
            Exception ex = new NullPointerException("npe");

            ResponseEntity<?> response = handler.handleGeneric(ex);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("error")).isEqualTo("Internal Server Error");
        }
    }
}
