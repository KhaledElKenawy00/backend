package com.virtualoffice.service.user.notifications;

import com.virtualoffice.service.user.domain.enumuration.NotificationType;
import com.virtualoffice.service.user.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationPublisher {

    private final RabbitTemplate notificationsRabbitTemplate;

    @Value("${notifications.exchange}")
    private String exchange;

    @Value("${notifications.routing.key}")
    private String routingKey;

    public void publish(NotificationType type, Map<String, Object> payload) {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now(),
                payload
        );

        try {
            notificationsRabbitTemplate.convertAndSend(exchange, routingKey, event);
        } catch (Exception e) {
            log.error("Failed to publish notification {}: {}", type, e.getMessage());
        }
    }
}