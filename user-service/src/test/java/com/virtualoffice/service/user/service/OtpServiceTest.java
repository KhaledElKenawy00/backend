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
import com.virtualoffice.service.user.domain.entity.VerificationRequest;
import com.virtualoffice.service.user.domain.enumuration.VerificationRequestStatus;
import com.virtualoffice.service.user.domain.enumuration.VerificationRequestType;
import com.virtualoffice.service.user.dto.ApiResponse;
import com.virtualoffice.service.user.repository.UserRepository;
import com.virtualoffice.service.user.repository.VerificationRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private VerificationRequestRepository verificationRequestRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OtpService otpService;

    private User user() {
        User u = new User();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setFirstName("Test");
        return u;
    }

    private VerificationRequest pending(LocalDateTime expiresAt) {
        return VerificationRequest.builder()
                .otp("hashed")
                .status(VerificationRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .type(VerificationRequestType.PASSWORD_RESET)
                .user(user())
                .build();
    }

    @Test
    void verifyReturnsNoPendingWhenNoOtpExists() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
        when(verificationRequestRepository.getOtpByUser_IdAndTypeAndStatus(
                1L, VerificationRequestType.PASSWORD_RESET, VerificationRequestStatus.PENDING))
                .thenReturn(Optional.empty());

        ApiResponse result = otpService.verifyOtp("user@example.com", "123456", VerificationRequestType.PASSWORD_RESET);

        assertThat(result.getStatus()).isEqualTo("No Pending OTP found");
        verify(verificationRequestRepository, never()).save(any());
    }

    @Test
    void verifyReturnsInvalidWhenOtpDoesNotMatch() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
        when(verificationRequestRepository.getOtpByUser_IdAndTypeAndStatus(
                1L, VerificationRequestType.PASSWORD_RESET, VerificationRequestStatus.PENDING))
                .thenReturn(Optional.of(pending(LocalDateTime.now().plusMinutes(5))));
        when(passwordEncoder.matches("000000", "hashed")).thenReturn(false);

        ApiResponse result = otpService.verifyOtp("user@example.com", "000000", VerificationRequestType.PASSWORD_RESET);

        assertThat(result.getStatus()).isEqualTo("Invalid OTP");
    }

    @Test
    void verifyApprovesWhenOtpMatches() {
        VerificationRequest request = pending(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
        when(verificationRequestRepository.getOtpByUser_IdAndTypeAndStatus(
                1L, VerificationRequestType.PASSWORD_RESET, VerificationRequestStatus.PENDING))
                .thenReturn(Optional.of(request));
        when(passwordEncoder.matches("654321", "hashed")).thenReturn(true);

        ApiResponse result = otpService.verifyOtp("user@example.com", "654321", VerificationRequestType.PASSWORD_RESET);

        assertThat(result.getStatus()).isEqualTo("OTP verified");
        assertThat(request.getStatus()).isEqualTo(VerificationRequestStatus.APPROVED);
        verify(verificationRequestRepository).save(request);
    }

    @Test
    void verifyThrowsWhenOtpExpired() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
        when(verificationRequestRepository.getOtpByUser_IdAndTypeAndStatus(
                1L, VerificationRequestType.PASSWORD_RESET, VerificationRequestStatus.PENDING))
                .thenReturn(Optional.of(pending(LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> otpService.verifyOtp(
                "user@example.com", "123456", VerificationRequestType.PASSWORD_RESET))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void requestReturnsUserNotFoundWhenUserMissing() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiResponse result = otpService.requestOtp("ghost@example.com", VerificationRequestType.PASSWORD_RESET);

        assertThat(result.getStatus()).isEqualTo("User not found");
        verify(verificationRequestRepository, never()).save(any());
    }

    @Test
    void requestGeneratesAndPersistsOtp() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        ApiResponse result = otpService.requestOtp("user@example.com", VerificationRequestType.PASSWORD_RESET);

        assertThat(result.getStatus()).isEqualTo("OTP generated");
        verify(verificationRequestRepository).save(any(VerificationRequest.class));
        verify(notificationService).otpNotification(any(User.class), anyString());
    }
}
