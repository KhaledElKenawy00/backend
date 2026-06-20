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
package com.virtualoffice.room_service.service.impl;

import com.virtualoffice.room_service.service.WebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebSocketTicketServiceImpl implements WebSocketTicketService {

    private final StringRedisTemplate redisTemplate;

    @Value("${room.redis.ticket-prefix}")
    private String ticketPrefix;

    @Value("${room.ws.ticket-ttl-seconds}")
    private long ticketTtlSeconds;

    @Override
    public String createTicket(Integer userId) {
        String ticket = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(ticketPrefix + ticket, String.valueOf(userId), Duration.ofSeconds(ticketTtlSeconds));
        return ticket;
    }

    @Override
    public Integer validateAndConsumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String value = redisTemplate.opsForValue().getAndDelete(ticketPrefix + ticket);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
