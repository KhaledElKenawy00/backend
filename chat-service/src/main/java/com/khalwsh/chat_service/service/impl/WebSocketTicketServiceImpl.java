package com.khalwsh.chat_service.service.impl;

import com.khalwsh.chat_service.service.WebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebSocketTicketServiceImpl implements WebSocketTicketService {

    private final StringRedisTemplate redisTemplate;

    private static final Duration TICKET_TTL = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "ws-ticket:";

    @Override
    public String createTicket(Integer userId, String role) {
        String ticket = UUID.randomUUID().toString();
        String value = userId + ":" + (role != null ? role : "USER");
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, value, TICKET_TTL);
        return ticket;
    }

    @Override
    public Map<String, Object> validateAndConsumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) return null;

        // GETDEL is atomic — guarantees a ticket can't be redeemed twice across racing handshakes
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        if (value == null) return null;

        String[] parts = value.split(":", 2);
        Integer userId;
        try {
            userId = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        String role = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "USER";
        return Map.of("userId", userId, "userRole", role);
    }
}
