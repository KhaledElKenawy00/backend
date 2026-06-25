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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthGatewayFilterTest {

    private static final String SECRET = "your-very-strong-secret-key-must-be-at-least-32-characters-long";

    private final AuthGatewayFilter filter =
            new AuthGatewayFilter(new JwtService(SECRET), List.of("/api/auth/", "/api/otp/"));

    /** A chain that records the (possibly mutated) exchange it is handed, or leaves it null if denied. */
    private static final class CapturingChain implements GatewayFilterChain {
        private ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }
    }

    private static String validToken(long userId) {
        return Jwts.builder()
                .setSubject("user@example.com")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .claim("userId", userId)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private static String header(ServerHttpRequest request, String name) {
        return request.getHeaders().getFirst(name);
    }

    @Test
    void protectedRequestWithValidTokenInjectsTrustedIdentityAndDropsSpoof() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/workspace/42")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken(7))
                        .header("X-User-Id", "999")   // spoof attempt
                        .header("X-User-Role", "ADMIN"));
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.captured.getRequest();
        assertThat(header(forwarded, "X-User-Id")).isEqualTo("7");   // from the token, not the spoof
        assertThat(header(forwarded, "X-User-Role")).isEqualTo("USER");
    }

    @Test
    void protectedRequestWithoutTokenIsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/workspace/42"));
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRequestWithInvalidTokenIsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chat/channels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void internalPathIsBlockedAtTheEdge() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/internal/workspace/1/zones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken(7)));
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void publicPathPassesThroughUnauthenticatedButStripsSpoof() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-User-Id", "999")); // no token, spoof attempt
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured).isNotNull(); // allowed through
        assertThat(header(chain.captured.getRequest(), "X-User-Id")).isNull(); // spoof stripped
    }

    @Test
    void healthEndpointsArePublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/chat/health"));
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured).isNotNull();
    }
}
