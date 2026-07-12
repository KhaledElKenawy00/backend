package com.virtualoffice.notifications.controller;

import com.virtualoffice.notifications.dto.WebSocketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/ws")
@RequiredArgsConstructor
@Slf4j
public class InternalPushController {

    private static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${internal.token:dev-internal-token}")
    private String internalToken;

    @PostMapping("/push/{userId}")
    public ResponseEntity<Void> push(
            @PathVariable Long userId,
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody Map<String, Object> body) {

        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String action = (String) body.getOrDefault("action", "EVENT");
        Object payload = body.get("payload");

        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    DESTINATION,
                    new WebSocketEvent(action, payload));
        } catch (Exception e) {
            log.warn("Failed to push WS event '{}' to user {}: {}", action, userId, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
