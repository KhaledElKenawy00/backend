package com.virtualoffice.chat_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
@Slf4j
public class NotificationsPushClient {

    private static final String PUSH_PATH = "/internal/ws/push/{userId}";

    private final RestClient restClient;

    public NotificationsPushClient(
            RestClient.Builder builder,
            @Value("${notifications.service.base-url:http://localhost:8082}") String baseUrl,
            @Value("${notifications.service.internal-token:dev-internal-token}") String token) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Token", token)
                .build();
    }

    public void pushMembershipUpdated(Integer userId, String type, Integer workspaceId) {
        try {
            restClient.post()
                    .uri(PUSH_PATH, userId)
                    .body(Map.of(
                            "action", "MEMBERSHIP_UPDATED",
                            "payload", Map.of("type", type, "workspaceId", workspaceId)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("failed to push MEMBERSHIP_UPDATED to user {}: {}", userId, e.getMessage());
        }
    }
}
