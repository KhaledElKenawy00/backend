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
import com.virtualoffice.service.user.dto.ApiResponse;
import com.virtualoffice.service.user.dto.UpdatePasswordRequest;
import com.virtualoffice.service.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private UserService userService;

    private static final String EMAIL = "user@example.com";

    @BeforeEach
    void setAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private User user() {
        User u = new User();
        u.setId(1L);
        u.setEmail(EMAIL);
        u.setFirstName("Test");
        u.setLastName("User");
        u.setPassword("oldHash");
        return u;
    }

    @Test
    void getUserDataReturnsProfileWithId() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        ResponseEntity<ApiResponse> response = userService.getUserData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFields().get("id")).isEqualTo(1L);
        assertThat(response.getBody().getFields().get("email")).isEqualTo(EMAIL);
    }

    @Test
    void updatePasswordRejectsWrongOldPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("wrong", "oldHash")).thenReturn(false);
        UpdatePasswordRequest req = new UpdatePasswordRequest();
        req.setOldPassword("wrong");
        req.setNewPassword("newPass");

        ResponseEntity<ApiResponse> response = userService.updatePassword(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
        verify(notificationService, never()).passwordResetNotification(any());
    }

    @Test
    void updatePasswordSavesNewHashAndNotifies() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("oldPass", "oldHash")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("newHash");
        UpdatePasswordRequest req = new UpdatePasswordRequest();
        req.setOldPassword("oldPass");
        req.setNewPassword("newPass");

        ResponseEntity<ApiResponse> response = userService.updatePassword(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userRepository).save(any(User.class));
        verify(notificationService).passwordResetNotification(any(User.class));
    }

    @Test
    void uploadPhotoRejectsNonImage() {
        MultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "hi".getBytes());

        ResponseEntity<ApiResponse> response = userService.uploadPhoto(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void uploadPhotoRejectsTooLarge() {
        byte[] big = new byte[10 * (1 << 20) + 1];
        MultipartFile file = new MockMultipartFile("file", "big.png", "image/png", big);

        ResponseEntity<ApiResponse> response = userService.uploadPhoto(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void uploadPhotoStoresValidImage() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
        MultipartFile file = new MockMultipartFile("file", "ok.png", "image/png", "png-bytes".getBytes());

        ResponseEntity<ApiResponse> response = userService.uploadPhoto(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void getCurrentUserProfileReturnsUser() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

        User result = userService.getCurrentUserProfile();

        assertThat(result.getEmail()).isEqualTo(EMAIL);
    }
}
