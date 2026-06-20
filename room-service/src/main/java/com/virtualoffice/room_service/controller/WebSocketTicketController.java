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
package com.virtualoffice.room_service.controller;

import com.virtualoffice.room_service.dto.response.WebSocketTicketResponse;
import com.virtualoffice.room_service.service.WebSocketTicketService;
import com.virtualoffice.room_service.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class WebSocketTicketController {

    private final WebSocketTicketService webSocketTicketService;

    @PostMapping("/ws-ticket")
    public ResponseEntity<WebSocketTicketResponse> createTicket(HttpServletRequest httpRequest) {
        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        String ticket = webSocketTicketService.createTicket(user.getUserId());
        return ResponseEntity.ok(WebSocketTicketResponse.builder().ticket(ticket).build());
    }
}
