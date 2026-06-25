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
package com.virtualoffice.workspace.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Publishes {@link WorkspaceChannelEvent}s to chat-service over RabbitMQ.
 *
 * <p>Events are sent <strong>after</strong> the surrounding transaction commits, so a rolled-back
 * membership change never provisions a phantom channel. Publishing is best-effort: a broker
 * outage is logged, not propagated — the user operation already succeeded and the event stream
 * is idempotent on the consumer side, so a later reconcile can recover.
 */
@Component
public class WorkspaceChannelEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceChannelEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public WorkspaceChannelEventPublisher(RabbitTemplate rabbitTemplate,
                                          @Value("${workspace.events.exchange}") String exchange,
                                          @Value("${workspace.events.channel.routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    /** Workspace created: provision the canonical channel with the owner as first member. */
    public void channelCreated(Long workspaceId, String name, Long ownerId) {
        publish(new WorkspaceChannelEvent(newId(), WorkspaceChannelEventType.WORKSPACE_CHANNEL_CREATE,
                workspaceId, name, ownerId, List.of(ownerId)));
    }

    /** A member became active (invitation accepted): add them to the workspace channel. */
    public void memberAdded(Long workspaceId, Long userId) {
        publish(new WorkspaceChannelEvent(newId(), WorkspaceChannelEventType.WORKSPACE_CHANNEL_ADD_MEMBER,
                workspaceId, null, userId, null));
    }

    /** A member was removed (desk deactivated): drop them from the workspace channel. */
    public void memberRemoved(Long workspaceId, Long userId) {
        publish(new WorkspaceChannelEvent(newId(), WorkspaceChannelEventType.WORKSPACE_CHANNEL_REMOVE_MEMBER,
                workspaceId, null, userId, null));
    }

    private void publish(WorkspaceChannelEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(WorkspaceChannelEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.debug("published {} for workspace {}", event.type(), event.workspaceId());
        } catch (Exception e) {
            log.error("failed to publish {} for workspace {}: {}", event.type(), event.workspaceId(), e.getMessage());
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
