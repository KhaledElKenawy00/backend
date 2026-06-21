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
import com.virtualoffice.service.user.dto.LoginRequest;
import com.virtualoffice.service.user.dto.UpdatePasswordRequest;
import com.virtualoffice.service.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public ResponseEntity<ApiResponse> getUserData() {
        User user = getCurrentUser();
        return ResponseEntity.ok(
                new ApiResponse("User retrieved")
                        .add("id", user.getId())
                        .add("firstName", user.getFirstName())
                        .add("lastName", user.getLastName())
                        .add("email", user.getEmail())
                        .add("phoneNumber", user.getPhoneNumber())
                        .add("accountStatus", user.getAccountStatus())
                        .add("isEmailVerified", user.isEmailVerified())
                        .add("isPhoneVerified", user.isPhoneVerified())
        );
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public ResponseEntity<ApiResponse> uploadPhoto(MultipartFile file) {

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse("Failed")
                    .add("Error", "Invalid Image")
            );
        }

        // Maximum size Must be 10MB -> 10 * 2^(20)
        if (file.getSize() > 10 * (1 << 20)) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse("Failed")
                    .add("Error", "Image size is too large")
            );
        }

        // Get the current user and save the photo
        User user = getCurrentUser();
        try {
            user.setProfilePicture(file.getBytes());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse("Failed")
                    .add("Error", "Couldn't read the file")
            );
        }
        user.setProfilePictureType(contentType);
        userRepository.save(user);

        return ResponseEntity.ok(new ApiResponse("succeeded"));
    }

    public ResponseEntity<ApiResponse> updatePassword(UpdatePasswordRequest request) {
        User user = getCurrentUser();

        // Check if the old password is correct or not
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(
                new ApiResponse("Failed")
                .add("Error", "Old password does not match")
            );
        }

        // Save new hashed password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // send a notification
        notificationService.passwordResetNotification(user);

        return ResponseEntity.ok(new ApiResponse("succeeded"));
    }

    public User getCurrentUserProfile() {
        return getCurrentUser();
    }


}
