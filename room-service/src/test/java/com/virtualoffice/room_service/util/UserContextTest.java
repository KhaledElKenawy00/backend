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
package com.virtualoffice.room_service.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserContextTest {

    private HttpServletRequest request(String userId, String role) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-User-Id")).thenReturn(userId);
        when(req.getHeader("X-User-Role")).thenReturn(role);
        return req;
    }

    @Test
    void shouldParseValidUser() {
        UserContext.UserInfo user = UserContext.fromRequest(request("42", "USER"));
        assertThat(user.getUserId()).isEqualTo(42);
        assertThat(user.isUser()).isTrue();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void shouldParseValidAdminCaseInsensitive() {
        UserContext.UserInfo user = UserContext.fromRequest(request("7", "admin"));
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void shouldReject401WhenUserIdMissing() {
        assertThatThrownBy(() -> UserContext.fromRequest(request(null, "USER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void shouldReject401WhenUserIdBlank() {
        assertThatThrownBy(() -> UserContext.fromRequest(request("   ", "USER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void shouldReject400WhenUserIdNotNumeric() {
        assertThatThrownBy(() -> UserContext.fromRequest(request("abc", "USER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void shouldReject400WhenRoleMissing() {
        assertThatThrownBy(() -> UserContext.fromRequest(request("42", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void shouldReject400WhenRoleInvalid() {
        assertThatThrownBy(() -> UserContext.fromRequest(request("42", "SUPERUSER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
