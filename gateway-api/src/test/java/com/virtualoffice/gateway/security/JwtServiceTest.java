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

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "your-very-strong-secret-key-must-be-at-least-32-characters-long";
    private final JwtService jwtService = new JwtService(SECRET);

    private static String token(String secret, Long userId, long ttlMillis) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
                .setSubject("user@example.com")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis));
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    @Test
    void extractsUserIdFromValidToken() {
        Optional<Long> userId = jwtService.verifyAndExtractUserId(token(SECRET, 42L, 60_000));

        assertThat(userId).contains(42L);
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        String foreign = "another-very-strong-secret-key-at-least-32-characters-long!!";

        assertThat(jwtService.verifyAndExtractUserId(token(foreign, 42L, 60_000))).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        assertThat(jwtService.verifyAndExtractUserId(token(SECRET, 42L, -1_000))).isEmpty();
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(jwtService.verifyAndExtractUserId("not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsValidTokenWithoutUserIdClaim() {
        assertThat(jwtService.verifyAndExtractUserId(token(SECRET, null, 60_000))).isEmpty();
    }
}
