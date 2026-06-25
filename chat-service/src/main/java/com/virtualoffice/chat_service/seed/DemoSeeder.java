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
package com.virtualoffice.chat_service.seed;

import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChannelType;
import com.virtualoffice.chat_service.model.Message;
import com.virtualoffice.chat_service.model.MessageType;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import com.virtualoffice.chat_service.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds demo chat for the seeded demo workspaces, keyed to the same users as the workspace-service
 * desks — workspace 1 has members 1..5, workspace 2 has members 6..10. Enabled only with
 * {@code demo.seed=true} (dev/run.sh sets it), and idempotent per workspace — it skips any workspace
 * whose canonical channel already exists. The demo workspaces are created via SQL, so their
 * provisioning events never fire; this fills that gap for local testing.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true")
@RequiredArgsConstructor
public class DemoSeeder implements CommandLineRunner {

    /** Each demo workspace and its members, mirroring R__seed_demo.sql in workspace-service. */
    private record DemoWorkspace(int workspaceId, List<Integer> members) {
    }

    private static final List<DemoWorkspace> WORKSPACES = List.of(
            new DemoWorkspace(1, List.of(1, 2, 3, 4, 5)),
            new DemoWorkspace(2, List.of(6, 7, 8, 9, 10)));

    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;

    @Override
    public void run(String... args) {
        WORKSPACES.forEach(this::seedWorkspace);
    }

    private void seedWorkspace(DemoWorkspace ws) {
        if (channelRepository.findCanonicalByWorkspaceId(ws.workspaceId()).isPresent()) {
            return;
        }

        Instant now = Instant.now();
        List<Integer> members = ws.members();
        Channel channel = channelRepository.save(Channel.builder()
                .type(ChannelType.GROUP)
                .workspaceId(ws.workspaceId())
                .name("general")
                .canonical(true)
                .members(new ArrayList<>(members))
                .createdBy(members.get(0))
                .createdAt(now)
                .updatedAt(now)
                .build());

        seedMessage(channel.getId(), members.get(0), "Welcome to the demo office! 👋", now.minusSeconds(180));
        seedMessage(channel.getId(), members.get(1), "Morning! Grabbing coffee then standup.", now.minusSeconds(90));
        seedMessage(channel.getId(), members.get(2), "Heading to the meeting room now.", now.minusSeconds(20));

        log.info("seeded demo chat channel {} for workspace {}", channel.getId(), ws.workspaceId());
    }

    private void seedMessage(ObjectId channelId, int senderId, String content, Instant at) {
        messageRepository.save(Message.builder()
                .channelId(channelId)
                .senderId(senderId)
                .senderRole("USER")
                .content(content)
                .type(MessageType.TEXT)
                .deleted(false)
                .createdAt(at)
                .updatedAt(at)
                .build());
    }
}
