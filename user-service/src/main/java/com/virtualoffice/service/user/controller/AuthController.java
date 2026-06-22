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
package com.virtualoffice.service.user.controller;

import com.virtualoffice.service.user.dto.AuthResponse;
import com.virtualoffice.service.user.dto.LoginRequest;
import com.virtualoffice.service.user.dto.RegisterRequest;
import com.virtualoffice.service.user.service.AuthService;
import com.virtualoffice.service.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final NotificationService notificationService;
    private final AuthService authService;

    // Post api to register new user
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        if (!"None".equals(authResponse.getErrorMessage())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(authResponse);
        }
        return ResponseEntity.ok(authResponse);
    }

    // Post api to login
    // Post instead of Get for security
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        if (!"None".equals(authResponse.getErrorMessage())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
        }
        return ResponseEntity.ok(authResponse);
    }
}
