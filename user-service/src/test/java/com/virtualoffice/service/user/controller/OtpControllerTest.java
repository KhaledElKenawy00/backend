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

import com.virtualoffice.service.user.domain.enumuration.VerificationRequestType;
import com.virtualoffice.service.user.dto.ApiResponse;
import com.virtualoffice.service.user.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OtpControllerTest {

    private OtpService otpService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        otpService = Mockito.mock(OtpService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OtpController(otpService)).build();
    }

    @Test
    void requestOtpReturnsStatus() throws Exception {
        when(otpService.requestOtp(eq("user@example.com"), eq(VerificationRequestType.PASSWORD_RESET)))
                .thenReturn(new ApiResponse("OTP generated"));

        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OTP generated"));
    }

    @Test
    void verifyOtpReturnsStatus() throws Exception {
        when(otpService.verifyOtp(eq("user@example.com"), eq("123456"), eq(VerificationRequestType.PASSWORD_RESET)))
                .thenReturn(new ApiResponse("OTP verified"));

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OTP verified"));
    }
}
