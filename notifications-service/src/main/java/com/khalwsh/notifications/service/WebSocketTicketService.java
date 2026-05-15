package com.khalwsh.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * One-time, short-lived tickets for WebSocket handshakes.
 *
 * Browsers can't set headers on a WS upgrade, so we mint a ticket via REST
 * (authenticated by X-User-Id) and the client passes it as ?ticket= on the
 * WS connect. Same pattern chat-service uses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketTicketService {

    private final StringRedisTemplate redis;

    @Value("${notifications.redis.ticket-prefix}")
    private String ticketPrefix;

    @Value("${notifications.ws.ticket-ttl-seconds}")
    private long ttlSeconds;

    /** Mints a fresh ticket bound to userId, stores in Redis with TTL. */
    public String createTicket(Long userId) {
        String ticket = UUID.randomUUID().toString();
        String key = ticketPrefix + ticket;
        redis.opsForValue().set(key, userId.toString(), Duration.ofSeconds(ttlSeconds));
        return ticket;
    }

    /**
     * Atomically reads and deletes the ticket (GETDEL). Returns the userId if
     * the ticket existed; empty if missing, expired, or already consumed.
     * Single-use by construction.
     */
    public Optional<Long> consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        String key = ticketPrefix + ticket;
        String value = redis.opsForValue().getAndDelete(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            log.warn("Ticket {} resolved to non-numeric userId: {}", ticket, value);
            return Optional.empty();
        }
    }
}
