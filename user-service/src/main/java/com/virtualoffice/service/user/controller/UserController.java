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

import com.virtualoffice.service.user.domain.entity.User;
import com.virtualoffice.service.user.dto.ApiResponse;
import com.virtualoffice.service.user.dto.UpdatePasswordRequest;
import com.virtualoffice.service.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse> updatePassword(
            @RequestBody UpdatePasswordRequest request) {
        return userService.updatePassword(request);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getMe() {
        return userService.getUserData();
    }

    @PostMapping("/me/photo")
    public ResponseEntity<ApiResponse> uploadPhoto(@RequestParam("file") MultipartFile file) {
        return userService.uploadPhoto(file);
    }

    @GetMapping("/me/photo")
    public ResponseEntity<byte[]> getPhoto() {
        User user = userService.getCurrentUserProfile();

        if (user.getProfilePicture() == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(user.getProfilePictureType()))
                .body(user.getProfilePicture());
    }

}
