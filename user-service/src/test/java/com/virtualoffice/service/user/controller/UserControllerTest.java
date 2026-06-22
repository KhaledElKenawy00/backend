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
import com.virtualoffice.service.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
    }

    @Test
    void getMeReturnsProfileWithId() throws Exception {
        when(userService.getUserData()).thenReturn(
                ResponseEntity.ok(new ApiResponse("User retrieved").add("id", 1L)));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updatePasswordDelegatesToService() throws Exception {
        when(userService.updatePassword(any())).thenReturn(
                ResponseEntity.ok(new ApiResponse("succeeded")));

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"a\",\"newPassword\":\"b\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"));
    }

    @Test
    void getPhotoReturnsImageWhenPresent() throws Exception {
        User user = new User();
        user.setProfilePicture("png-bytes".getBytes());
        user.setProfilePictureType("image/png");
        when(userService.getCurrentUserProfile()).thenReturn(user);

        mockMvc.perform(get("/api/users/me/photo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void getPhotoReturns404WhenAbsent() throws Exception {
        User user = new User();
        user.setProfilePicture(null);
        when(userService.getCurrentUserProfile()).thenReturn(user);

        mockMvc.perform(get("/api/users/me/photo"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadPhotoDelegatesToService() throws Exception {
        when(userService.uploadPhoto(any())).thenReturn(
                ResponseEntity.ok(new ApiResponse("succeeded")));
        MockMultipartFile file = new MockMultipartFile("file", "p.png", "image/png", "data".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"));
    }
}
