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
package com.virtualoffice.notifications.controller;

import com.virtualoffice.notifications.config.GlobalExceptionHandler;
import com.virtualoffice.notifications.service.EmailDispatchService;
import com.virtualoffice.notifications.template.EmailTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PublishControllerTest {

    @Mock private EmailDispatchService emailDispatchService;

    @InjectMocks private PublishController controller;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void happyPathReturns200AndEventId() throws Exception {
        String body = """
            {
              "template": "SIGNUP_SUCCESS",
              "to": "user@example.com",
              "vars": { "firstName": "Khaled" }
            }
            """;

        mockMvc().perform(post("/api/notifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.eventId").value(notNullValue()));

        verify(emailDispatchService).dispatch(eq(EmailTemplate.SIGNUP_SUCCESS), eq("user@example.com"), any());
    }

    @Test
    void invalidEmailReturns400() throws Exception {
        String body = """
            { "template": "SIGNUP_SUCCESS", "to": "not-an-email", "vars": {} }
            """;

        mockMvc().perform(post("/api/notifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void missingTemplateReturns400() throws Exception {
        String body = """
            { "to": "user@example.com", "vars": {} }
            """;

        mockMvc().perform(post("/api/notifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dispatchFailureReturns500WithEventId() throws Exception {
        doThrow(new RuntimeException("SMTP refused"))
                .when(emailDispatchService).dispatch(any(), any(), any());

        String body = """
            {
              "template": "OTP",
              "to": "user@example.com",
              "vars": { "otp": "123456" }
            }
            """;

        mockMvc().perform(post("/api/notifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.eventId").value(notNullValue()));
    }
}
