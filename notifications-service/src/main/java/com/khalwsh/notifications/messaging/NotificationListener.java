package com.khalwsh.notifications.messaging;

import com.khalwsh.notifications.service.EmailDispatchService;
import com.khalwsh.notifications.template.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final EmailDispatchService emailDispatchService;

    @RabbitListener(queues = "${notifications.queue}")
    public void handle(NotificationEvent event) {
        switch (event.getType()) {
            case SIGNUP_SUCCESS, LOGIN_SUCCESS, OTP, PASSWORD_RESET_SUCCESS -> handleEmail(event);
            case TASK_ASSIGNED -> {
                // stub — wired in Phase 3 (Step 18)
            }
        }
    }

    private void handleEmail(NotificationEvent event) {
        Object recipient = event.getPayload() != null ? event.getPayload().get("email") : null;
        if (!(recipient instanceof String to) || to.isBlank()) {
            log.error("Missing 'email' in payload for {} event {}", event.getType(), event.getEventId());
            throw new AmqpRejectAndDontRequeueException("missing recipient email");
        }

        emailDispatchService.dispatch(
                EmailTemplate.fromType(event.getType()),
                to,
                stringifyPayload(event.getPayload()));
    }

    /**
     * The renderer expects Map<String, String> for placeholder substitution.
     * Payload values arrive as Object on the wire (ints, instants, ...), so we
     * stringify them here. Null values become empty strings.
     */
    private Map<String, String> stringifyPayload(Map<String, Object> payload) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? "" : e.getValue().toString());
        }
        return out;
    }
}
