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
