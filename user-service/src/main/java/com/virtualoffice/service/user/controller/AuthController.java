package com.virtualoffice.service.user.controller;

import com.virtualoffice.service.user.dto.AuthResponse;
import com.virtualoffice.service.user.dto.LoginRequest;
import com.virtualoffice.service.user.dto.RegisterRequest;
import com.virtualoffice.service.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Post api to register new user
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        if (!authResponse.getErrorMessage().equals("None")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(authResponse);
        }
        return ResponseEntity.ok(authResponse);
    }

    // Post api to login
    // Post instead of Get for security
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        if (!authResponse.getErrorMessage().equals("None")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(authResponse);
        }
        return ResponseEntity.ok(authResponse);
    }
}