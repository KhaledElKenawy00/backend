package com.khalwsh.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

// Browsers cannot set headers on a WebSocket handshake, so we issue a
// short-lived ticket over REST (where X-User-Id is set by the gateway)
// and the client passes it back as a query string param on the WS connect.
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketTicketService {

    private final StringRedisTemplate redis;

    @Value("${notifications.redis.ticket-prefix}")
    private String ticketPrefix;

    @Value("${notifications.ws.ticket-ttl-seconds}")
    private long ttlSeconds;

    public String createTicket(Long userId) {
        String ticket = UUID.randomUUID().toString();
        String key = ticketPrefix + ticket;
        redis.opsForValue().set(key, userId.toString(), Duration.ofSeconds(ttlSeconds));
        return ticket;
    }

    // Atomic GETDEL: the ticket is consumed on the first successful read,
    // so a replay returns empty even if it happens within the TTL window.
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
