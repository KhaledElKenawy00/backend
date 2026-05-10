package com.virtualoffice.service.user.controller;

import com.virtualoffice.service.user.dto.AuthResponse;
import com.virtualoffice.service.user.dto.LoginRequest;
import com.virtualoffice.service.user.dto.RegisterRequest;
import com.virtualoffice.service.user.service.AuthService;
import lombok.RequiredArgsConstructor;
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
        return ResponseEntity.ok(authService.register(request));
    }

    // Post api to login
    // Post instead of Get for security
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        if (!authResponse.getErrorMessage().equals("None")) {
            return ResponseEntity.badRequest().body(authResponse);
        }
        return ResponseEntity.ok(authResponse);
    }
}