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
package com.virtualoffice.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * The trust boundary (INTEGRATION.md D7). For every request the gateway:
 * <ol>
 *   <li><b>strips</b> any client-supplied {@code X-User-*} header, so identity can never be spoofed
 *       — downstream services trust these headers precisely because only the gateway sets them;</li>
 *   <li><b>blocks</b> {@code /api/internal/**}, which is server-to-server only and must be
 *       unreachable from the edge;</li>
 *   <li>lets <b>public</b> paths (auth, otp, health) through unauthenticated;</li>
 *   <li>otherwise <b>requires</b> a valid Bearer JWT and injects the trusted {@code X-User-Id} (and
 *       {@code X-User-Role}) from its verified claims, answering 401 when the token is missing or
 *       invalid.</li>
 * </ol>
 */
@Component
public class AuthGatewayFilter implements GlobalFilter, Ordered {

    static final String USER_ID = "X-User-Id";
    static final String USER_ROLE = "X-User-Role";
    // No platform-level (USER/ADMIN) role is modelled yet; default everyone to USER. Workspace-scoped
    // roles are resolved separately by each service via workspace-service.
    static final String DEFAULT_ROLE = "USER";
    private static final String INTERNAL_PREFIX = "/api/internal/";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final List<String> publicPathPrefixes;

    public AuthGatewayFilter(JwtService jwtService,
                             @Value("${gateway.public-path-prefixes}") List<String> publicPathPrefixes) {
        this.jwtService = jwtService;
        this.publicPathPrefixes = publicPathPrefixes;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // (1) Never trust inbound identity headers.
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID);
                    h.remove(USER_ROLE);
                })
                .build();

        // (2) Internal endpoints are not reachable through the gateway.
        if (path.startsWith(INTERNAL_PREFIX)) {
            return deny(exchange, HttpStatus.FORBIDDEN);
        }

        // (3) Public endpoints pass through unauthenticated (still header-stripped).
        if (isPublic(path)) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        // (4) Protected endpoints require a valid token; inject the trusted identity.
        String token = bearerToken(stripped);
        if (token == null) {
            return deny(exchange, HttpStatus.UNAUTHORIZED);
        }
        Optional<Long> userId = jwtService.verifyAndExtractUserId(token);
        if (userId.isEmpty()) {
            return deny(exchange, HttpStatus.UNAUTHORIZED);
        }

        ServerHttpRequest authed = stripped.mutate()
                .headers(h -> {
                    h.set(USER_ID, String.valueOf(userId.get()));
                    h.set(USER_ROLE, DEFAULT_ROLE);
                })
                .build();
        return chain.filter(exchange.mutate().request(authed).build());
    }

    private boolean isPublic(String path) {
        if (path.endsWith("/health")) {
            return true;
        }
        for (String prefix : publicPathPrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length()).trim();
        }
        return null;
    }

    private static Mono<Void> deny(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1; // run before the routing filters
    }
}
