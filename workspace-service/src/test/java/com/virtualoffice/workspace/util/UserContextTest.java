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
package com.virtualoffice.workspace.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextTest {

    private MockHttpServletRequest request(String id, String role) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (id != null) req.addHeader("X-User-Id", id);
        if (role != null) req.addHeader("X-User-Role", role);
        return req;
    }

    @Test
    void parsesValidUserAndRole() {
        UserContext.UserInfo info = UserContext.fromRequest(request("42", "ADMIN"));
        assertThat(info.getUserId()).isEqualTo(42L);
        assertThat(info.isAdmin()).isTrue();
        assertThat(info.isUser()).isFalse();

        UserContext.UserInfo user = UserContext.fromRequest(request("7", "user"));
        assertThat(user.isUser()).isTrue();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void missingUserIdIsUnauthorized() {
        assertThatThrownBy(() -> UserContext.fromRequest(request(null, "USER")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void nonNumericUserIdIsBadRequest() {
        assertThatThrownBy(() -> UserContext.fromRequest(request("abc", "USER")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void invalidRoleIsBadRequest() {
        assertThatThrownBy(() -> UserContext.fromRequest(request("1", "SUPERVISOR")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> UserContext.fromRequest(request("1", null)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
