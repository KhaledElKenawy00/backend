package com.khalwsh.notifications.service;

import com.khalwsh.notifications.messaging.NotificationEvent;
import com.khalwsh.notifications.model.Notification;
import com.khalwsh.notifications.repository.NotificationRepository;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationService {

    private final NotificationRepository repository;
    private final MongoTemplate mongoTemplate;

    // Returns Optional.empty when the event was already persisted under the
    // same eventId, so callers can detect redelivery and skip side effects
    // (e.g. WebSocket push) for it.
    public Optional<Notification> createFromEvent(NotificationEvent event) {
        Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();

        Long userId = readLong(payload, "assigneeUserId");
        if (userId == null) {
            log.error("Missing 'assigneeUserId' in payload for {} event {}", event.getType(), event.getEventId());
            return Optional.empty();
        }

        String assignedByName = readString(payload, "assignedByName");
        String taskTitle      = readString(payload, "taskTitle");

        String title = "New task assigned";
        String body  = (assignedByName != null ? assignedByName : "Someone")
                + " assigned you \"" + (taskTitle != null ? taskTitle : "a task") + "\"";

        Map<String, Object> data = new HashMap<>();
        data.put("taskId",      payload.get("taskId"));
        data.put("assignedBy",  payload.get("assignedBy"));
        data.put("workspaceId", payload.get("workspaceId"));
        data.put("dueAt",       payload.get("dueAt"));

        Notification notification = Notification.builder()
                .userId(userId)
                .type(event.getType())
                .title(title)
                .body(body)
                .data(data)
                .read(false)
                .eventId(event.getEventId())
                .createdAt(Instant.now())
                .build();

        try {
            return Optional.of(repository.save(notification));
        } catch (DuplicateKeyException e) {
            log.debug("Duplicate event {} ignored (idempotent insert)", event.getEventId());
            return Optional.empty();
        }
    }

    public Page<Notification> list(Long userId, int page, int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    // Cross-user access is indistinguishable from "not found" so we never
    // leak existence of another user's notification.
    public boolean markRead(String id, Long userId) {
        return repository.findByIdAndUserId(id, userId)
                .map(n -> {
                    if (!n.isRead()) {
                        n.setRead(true);
                        n.setReadAt(Instant.now());
                        repository.save(n);
                    }
                    return true;
                })
                .orElse(false);
    }

    // Single bulk-update round trip rather than fetch-then-save-each.
    public long markAllRead(Long userId) {
        Query query = new Query(
                Criteria.where("userId").is(userId)
                        .and("read").is(false));

        Update update = new Update()
                .set("read", true)
                .set("readAt", Instant.now());

        UpdateResult result = mongoTemplate.updateMulti(query, update, Notification.class);
        return result.getModifiedCount();
    }

    public boolean delete(String id, Long userId) {
        return repository.findByIdAndUserId(id, userId)
                .map(n -> {
                    repository.delete(n);
                    return true;
                })
                .orElse(false);
    }

    private Long readLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String readString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}
