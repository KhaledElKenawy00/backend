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
package com.virtualoffice.service.user.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long-1234567890";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000L);
    }

    private UserDetails userDetails(String email) {
        return User.withUsername(email).password("x").authorities(Collections.emptyList()).build();
    }

    @Test
    void generatedTokenRoundTripsEmail() {
        String token = jwtUtil.generateToken("user@example.com");

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void generatedTokenRoundTripsUserId() {
        String token = jwtUtil.generateToken("user@example.com", 42L);

        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void legacyTokenWithoutUserIdClaimYieldsNull() {
        String token = jwtUtil.generateToken("user@example.com");

        assertThat(jwtUtil.extractUserId(token)).isNull();
    }

    @Test
    void tokenIsValidForMatchingUser() {
        String token = jwtUtil.generateToken("user@example.com");

        assertThat(jwtUtil.isTokenValid(token, userDetails("user@example.com"))).isTrue();
    }

    @Test
    void tokenIsInvalidForDifferentUser() {
        String token = jwtUtil.generateToken("user@example.com");

        assertThat(jwtUtil.isTokenValid(token, userDetails("someone@example.com"))).isFalse();
    }

    @Test
    void expiredTokenThrows() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1000L);
        String token = jwtUtil.generateToken("user@example.com");

        assertThatThrownBy(() -> jwtUtil.extractEmail(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenThrows() {
        String token = jwtUtil.generateToken("user@example.com") + "tampered";

        assertThatThrownBy(() -> jwtUtil.extractEmail(token))
                .isInstanceOf(JwtException.class);
    }
}
