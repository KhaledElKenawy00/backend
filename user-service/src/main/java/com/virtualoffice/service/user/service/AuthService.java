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
import com.virtualoffice.service.user.domain.enumuration.AccountStatus;
import com.virtualoffice.service.user.dto.AuthResponse;
import com.virtualoffice.service.user.dto.LoginRequest;
import com.virtualoffice.service.user.dto.RegisterRequest;
import com.virtualoffice.service.user.repository.UserRepository;
import com.virtualoffice.service.user.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {

        // Check if email is already taken
        if (userRepository.existsByEmail(request.getEmail())) {
            return AuthResponse.withError("Such E-mail Already Exist");
        }

        // Build and save the new user
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // hash the password
                .phoneNumber(request.getPhoneNumber())
                .accountStatus(AccountStatus.ACTIVE)
                .isEmailVerified(false)
                .isPhoneVerified(false)
                .isDisabled(false)
                .build();

        userRepository.save(user);

        // Generate a JWT token for the new user
        String token = jwtUtil.generateToken(user.getEmail());

        // Return the token
        return new AuthResponse(token, user.getEmail(), user.getFirstName(), user.getLastName(), "None");
    }

    public AuthResponse login(LoginRequest request) {

        // Check Authentication first
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);
        if (user == null) {
            return AuthResponse.withError("User Not Found");
        }

        String token = jwtUtil.generateToken(user.getEmail());

        return new AuthResponse(token, user.getEmail(), user.getFirstName(), user.getLastName(), "None");
    }
}
