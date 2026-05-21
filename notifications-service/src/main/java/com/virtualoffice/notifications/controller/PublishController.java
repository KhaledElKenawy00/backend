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

import com.virtualoffice.notifications.dto.SendEmailRequest;
import com.virtualoffice.notifications.service.EmailDispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PublishController {

    private final EmailDispatchService emailDispatchService;

    @PostMapping("/api/notifications/email")
    public ResponseEntity<Map<String, String>> sendEmail(@Valid @RequestBody SendEmailRequest request) {
        String eventId = UUID.randomUUID().toString();
        try {
            emailDispatchService.dispatch(request.getTemplate(), request.getTo(), request.getVars());
            return ResponseEntity.ok(Map.of("status", "sent", "eventId", eventId));
        } catch (RuntimeException e) {
            log.error("Failed to dispatch email [eventId={}]", eventId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "eventId", eventId));
        }
    }
}
