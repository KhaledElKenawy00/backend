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
package com.virtualoffice.chat_service.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserContextTest {

    private HttpServletRequest mockRequest(String userId, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    // ────────────────────────────────────────
    // fromRequest — happy path
    // ────────────────────────────────────────

    @Nested
    class FromRequestHappyPath {

        @Test
        void shouldParseValidUserHeaders() {
            HttpServletRequest request = mockRequest("10", "USER");

            UserContext.UserInfo info = UserContext.fromRequest(request);

            assertThat(info.getUserId()).isEqualTo(10);
            assertThat(info.getRole()).isEqualTo("USER");
        }

        @Test
        void shouldParseAdminRole() {
            HttpServletRequest request = mockRequest("42", "ADMIN");

            UserContext.UserInfo info = UserContext.fromRequest(request);

            assertThat(info.getUserId()).isEqualTo(42);
            assertThat(info.getRole()).isEqualTo("ADMIN");
        }

        @Test
        void shouldAcceptLowercaseRole() {
            HttpServletRequest request = mockRequest("1", "user");

            UserContext.UserInfo info = UserContext.fromRequest(request);

            assertThat(info.getUserId()).isEqualTo(1);
            assertThat(info.getRole()).isEqualTo("user");
        }

        @Test
        void shouldAcceptMixedCaseAdmin() {
            HttpServletRequest request = mockRequest("1", "Admin");

            UserContext.UserInfo info = UserContext.fromRequest(request);

            assertThat(info.getRole()).isEqualTo("Admin");
        }
    }

    // ────────────────────────────────────────
    // fromRequest — missing/invalid headers
    // ────────────────────────────────────────

    @Nested
    class FromRequestValidation {

        @Test
        void shouldThrow401WhenUserIdMissing() {
            HttpServletRequest request = mockRequest(null, "USER");

            assertThatThrownBy(() -> UserContext.fromRequest(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("missing X-User-Id");
        }

        @Test
        void shouldThrow401WhenUserIdBlank() {
            HttpServletRequest request = mockRequest("   ", "USER");

            assertThatThrownBy(() -> UserContext.fromRequest(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("missing X-User-Id");
        }

        @Test
        void shouldThrow400WhenUserIdNotANumber() {
            HttpServletRequest request = mockRequest("abc", "USER");

            assertThatThrownBy(() -> UserContext.fromRequest(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("X-User-Id must be a valid integer");
        }

        @Test
        void shouldThrow400WhenUserIdIsFloat() {
            HttpServletRequest request = mockRequest("3.14", "USER");

            assertThatThrownBy(() -> UserContext.fromRequest(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("X-User-Id must be a valid integer");
        }

        @Test
        void shouldThrow400WhenRoleMissing() {
            HttpServletRequest request = mockRequest("10", null);

            assertThatThrownBy(() -> UserContext.fromRequest(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("X-User-Role must be USER or ADMIN");
        }

        @Test
        void shouldThrow400WhenRoleInvalid() {
            HttpServletRequest request = mockRequest("10", "SUPERADMIN");

            assertThatThrownBy(() -> UserContext.fromRequest(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("X-User-Role must be USER or ADMIN");
        }

        @Test
        void shouldThrow400WhenRoleEmpty() {
            HttpServletRequest request = mockRequest("10", "");

            assertThatThrownBy(() -> UserContext.fromRequest(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("X-User-Role must be USER or ADMIN");
        }
    }

    // ────────────────────────────────────────
    // UserInfo helper methods
    // ────────────────────────────────────────

    @Nested
    class UserInfoHelpers {

        @Test
        void isAdminShouldReturnTrueForAdmin() {
            UserContext.UserInfo info = new UserContext.UserInfo(1, "ADMIN");
            assertThat(info.isAdmin()).isTrue();
        }

        @Test
        void isAdminShouldReturnTrueForLowercaseAdmin() {
            UserContext.UserInfo info = new UserContext.UserInfo(1, "admin");
            assertThat(info.isAdmin()).isTrue();
        }

        @Test
        void isAdminShouldReturnFalseForUser() {
            UserContext.UserInfo info = new UserContext.UserInfo(1, "USER");
            assertThat(info.isAdmin()).isFalse();
        }

        @Test
        void isUserShouldReturnTrueForUser() {
            UserContext.UserInfo info = new UserContext.UserInfo(1, "USER");
            assertThat(info.isUser()).isTrue();
        }

        @Test
        void isUserShouldReturnFalseForAdmin() {
            UserContext.UserInfo info = new UserContext.UserInfo(1, "ADMIN");
            assertThat(info.isUser()).isFalse();
        }

        @Test
        void getUserIdShouldReturnCorrectId() {
            UserContext.UserInfo info = new UserContext.UserInfo(99, "USER");
            assertThat(info.getUserId()).isEqualTo(99);
        }

        @Test
        void getRoleShouldReturnCorrectRole() {
            UserContext.UserInfo info = new UserContext.UserInfo(1, "ADMIN");
            assertThat(info.getRole()).isEqualTo("ADMIN");
        }
    }
}
