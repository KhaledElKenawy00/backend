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
package com.virtualoffice.chat_service.controller;

import com.virtualoffice.chat_service.service.WebSocketTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.virtualoffice.chat_service.dto.response.WebSocketTicketResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketTicketControllerTest {

    @Mock
    private WebSocketTicketService webSocketTicketService;

    @InjectMocks
    private WebSocketTicketController controller;

    private HttpServletRequest mockRequest(String userId, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    @Nested
    class CreateTicket {

        @Test
        void shouldReturnTicketForUser() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(webSocketTicketService.createTicket(10, "USER")).thenReturn("ticket-abc");

            ResponseEntity<WebSocketTicketResponse> response = controller.createTicket(httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getTicket()).isEqualTo("ticket-abc");
        }

        @Test
        void shouldReturnTicketForAdmin() {
            HttpServletRequest httpRequest = mockRequest("42", "ADMIN");
            when(webSocketTicketService.createTicket(42, "ADMIN")).thenReturn("admin-ticket");

            ResponseEntity<WebSocketTicketResponse> response = controller.createTicket(httpRequest);

            assertThat(response.getBody().getTicket()).isEqualTo("admin-ticket");
        }

        @Test
        void shouldPassRoleToService() {
            HttpServletRequest httpRequest = mockRequest("5", "USER");
            when(webSocketTicketService.createTicket(5, "USER")).thenReturn("t");

            controller.createTicket(httpRequest);

            verify(webSocketTicketService).createTicket(5, "USER");
        }
    }
}
