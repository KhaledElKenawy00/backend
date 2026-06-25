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
package com.virtualoffice.chat_service.messaging;

import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChannelType;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Provisions and syncs the canonical per-workspace chat channel from workspace-service events
 * (INTEGRATION.md §5.1). Sibling of {@link RoomChannelListener}; idempotent so redeliveries and
 * out-of-order events are safe. The canonical channel is keyed by {@code workspaceId} (workspace
 * -service does not know chat-side ids), resolved via {@link ChannelRepository#findCanonicalByWorkspaceId}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceChannelListener {

    private static final String DEFAULT_CHANNEL_NAME = "general";

    private final ChannelRepository channelRepository;

    @RabbitListener(queues = "${workspace.channel.queue}")
    public void handle(WorkspaceChannelEvent event) {
        if (event.getType() == null || event.getWorkspaceId() == null) {
            log.warn("dropping malformed workspace-channel event: {}", event);
            return;
        }
        switch (event.getType()) {
            case WORKSPACE_CHANNEL_CREATE -> createChannel(event);
            case WORKSPACE_CHANNEL_ADD_MEMBER -> addMember(event);
            case WORKSPACE_CHANNEL_REMOVE_MEMBER -> removeMember(event);
        }
    }

    private void createChannel(WorkspaceChannelEvent event) {
        if (channelRepository.findCanonicalByWorkspaceId(event.getWorkspaceId()).isPresent()) {
            return; // already provisioned — idempotent
        }

        List<Integer> members = event.getMembers() != null
                ? new ArrayList<>(event.getMembers())
                : new ArrayList<>();

        Instant now = Instant.now();
        Channel channel = Channel.builder()
                .name(event.getName() != null ? event.getName() : DEFAULT_CHANNEL_NAME)
                .type(ChannelType.GROUP)
                .workspaceId(event.getWorkspaceId())
                .members(members)
                .canonical(true)
                .createdBy(event.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        channelRepository.save(channel);
        log.info("provisioned canonical channel for workspace {}", event.getWorkspaceId());
    }

    private void addMember(WorkspaceChannelEvent event) {
        Channel channel = canonicalOrWarn(event);
        if (channel == null || event.getUserId() == null) {
            return;
        }
        channelRepository.addMember(channel.getId(), event.getUserId(), Instant.now());
    }

    private void removeMember(WorkspaceChannelEvent event) {
        Channel channel = canonicalOrWarn(event);
        if (channel == null || event.getUserId() == null) {
            return;
        }
        channelRepository.removeMember(channel.getId(), event.getUserId(), Instant.now());
    }

    private Channel canonicalOrWarn(WorkspaceChannelEvent event) {
        return channelRepository.findCanonicalByWorkspaceId(event.getWorkspaceId())
                .orElseGet(() -> {
                    // CREATE is emitted at workspace creation, before any membership event, so this
                    // is only reachable on a lost CREATE; log rather than silently dropping membership.
                    log.warn("no canonical channel for workspace {}; dropping {}",
                            event.getWorkspaceId(), event.getType());
                    return null;
                });
    }
}
