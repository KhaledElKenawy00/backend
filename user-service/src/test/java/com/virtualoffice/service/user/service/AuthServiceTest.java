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
package com.virtualoffice.service.user.service;

import com.virtualoffice.service.user.domain.entity.User;
import com.virtualoffice.service.user.dto.AuthResponse;
import com.virtualoffice.service.user.dto.LoginRequest;
import com.virtualoffice.service.user.dto.RegisterRequest;
import com.virtualoffice.service.user.repository.UserRepository;
import com.virtualoffice.service.user.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Test");
        req.setLastName("User");
        req.setEmail("user@example.com");
        req.setPassword("Passw0rd!");
        req.setPhoneNumber("123");
        return req;
    }

    @Test
    void registerCreatesUserAndReturnsTokenWithId() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(5L);
            return u;
        });
        when(jwtUtil.generateToken("user@example.com")).thenReturn("token-123");

        AuthResponse response = authService.register(registerRequest());

        assertThat(response.getErrorMessage()).isEqualTo("None");
        assertThat(response.getToken()).isEqualTo("token-123");
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getId()).isEqualTo(5L);
        verify(notificationService).registerNotification(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        AuthResponse response = authService.register(registerRequest());

        assertThat(response.getErrorMessage()).isEqualTo("Such E-mail Already Exist");
        assertThat(response.getToken()).isNull();
        verify(userRepository, never()).save(any());
        verify(notificationService, never()).registerNotification(any());
    }

    @Test
    void loginAuthenticatesAndReturnsTokenWithId() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("Passw0rd!");
        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.com");
        user.setFirstName("Test");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("user@example.com")).thenReturn("token-xyz");

        AuthResponse response = authService.login(req);

        assertThat(response.getErrorMessage()).isEqualTo("None");
        assertThat(response.getToken()).isEqualTo("token-xyz");
        assertThat(response.getId()).isEqualTo(7L);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void loginReturnsErrorWhenUserMissingAfterAuthentication() {
        LoginRequest req = new LoginRequest();
        req.setEmail("ghost@example.com");
        req.setPassword("Passw0rd!");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        AuthResponse response = authService.login(req);

        assertThat(response.getErrorMessage()).isEqualTo("User Not Found");
        assertThat(response.getToken()).isNull();
    }
}
