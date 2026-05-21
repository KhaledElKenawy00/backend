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

import com.virtualoffice.notifications.dto.InboxPage;
import com.virtualoffice.notifications.dto.NotificationResponse;
import com.virtualoffice.notifications.model.Notification;
import com.virtualoffice.notifications.service.InAppNotificationService;
import com.virtualoffice.notifications.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class InboxController {

    private static final int MAX_PAGE_SIZE = 100;

    private final InAppNotificationService service;
    private final UserContext userContext;


    @GetMapping
    public InboxPage list(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 1");
        }
        if (size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be >= 1");
        }
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }

        Long userId = userContext.currentUserId();
        Page<Notification> p = service.list(userId, page - 1, size);

        return InboxPage.builder()
                .items(p.stream().map(NotificationResponse::from).toList())
                .page(p.getNumber() + 1)
                .size(p.getSize())
                .total(p.getTotalElements())
                .unreadCount(service.unreadCount(userId))
                .build();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        Long userId = userContext.currentUserId();
        return Map.of("unread", service.unreadCount(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id) {
        Long userId = userContext.currentUserId();
        return service.markRead(id, userId)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PatchMapping("/read-all")
    public Map<String, Long> markAllRead() {
        Long userId = userContext.currentUserId();
        return Map.of("updated", service.markAllRead(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        Long userId = userContext.currentUserId();
        return service.delete(id, userId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
