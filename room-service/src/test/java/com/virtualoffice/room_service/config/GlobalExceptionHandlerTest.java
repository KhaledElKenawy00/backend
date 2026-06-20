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
package com.virtualoffice.room_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mongoFailureMapsTo503() {
        ResponseEntity<Map<String, Object>> r = handler.handleMongoFailure(new DataAccessResourceFailureException("down"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void redisFailureMapsTo503() {
        ResponseEntity<Map<String, Object>> r = handler.handleRedisFailure(new RedisConnectionFailureException("down"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void amqpFailureMapsTo503() {
        ResponseEntity<Map<String, Object>> r = handler.handleAmqpFailure(new AmqpException("down"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody().get("message")).isNotNull();
    }

    @Test
    void illegalArgumentMapsTo400() {
        ResponseEntity<Map<String, Object>> r = handler.handleIllegalArgument(new IllegalArgumentException("bad id"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("message")).isEqualTo("bad id");
    }

    @Test
    void responseStatusIsForwarded() {
        ResponseEntity<Map<String, Object>> r = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().get("message")).isEqualTo("not a member");
    }

    @Test
    void genericMapsTo500() {
        ResponseEntity<Map<String, Object>> r = handler.handleGeneric(new RuntimeException("boom"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
