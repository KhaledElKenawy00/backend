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
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UserContext {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private static final String USER = "USER";
    private static final String ADMIN = "ADMIN";

    @Getter
    @AllArgsConstructor
    public static class UserInfo {
        private final Integer userId;
        private final String role;

        public boolean isAdmin() {
            return ADMIN.equalsIgnoreCase(role);
        }
        public boolean isUser() {
            return USER.equalsIgnoreCase(role);
        }
    }

    public static UserInfo fromRequest(HttpServletRequest request) {
        String userIdHeader = request.getHeader(HEADER_USER_ID);
        String role = request.getHeader(HEADER_USER_ROLE);

        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing X-User-Id header");
        }

        Integer userId;
        try {
            userId = Integer.parseInt(userIdHeader);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id must be a valid integer");
        }

        if (role == null || (!ADMIN.equalsIgnoreCase(role) && !USER.equalsIgnoreCase(role))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Role must be USER or ADMIN");
        }

        return new UserInfo(userId, role);
    }
}
