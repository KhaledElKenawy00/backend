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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guards {@code /api/internal/**} — these endpoints are server-to-server only
 * (Colyseus, chat-service, room-service) and must never be reachable by a browser.
 * The gateway also blocks the prefix at the edge; this is the in-service backstop.
 * Callers must present a matching {@code X-Internal-Token} shared secret.
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    static final String INTERNAL_PREFIX = "/api/internal/";
    static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final String internalToken;

    public InternalAuthFilter(@Value("${workspace.internal.token}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith(INTERNAL_PREFIX)) {
            String provided = request.getHeader(HEADER_INTERNAL_TOKEN);
            if (provided == null || !provided.equals(internalToken)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"internal endpoint\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
