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
 * Seeds demo chat for the seeded demo workspace (id 1), keyed to the same users as the
 * workspace-service desks (1, 2, 3). Enabled only with {@code demo.seed=true} (dev/run.sh sets it),
 * and idempotent — it does nothing if the canonical channel already exists. The demo workspace is
 * created via SQL, so its provisioning event never fires; this fills that gap for local testing.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true")
@RequiredArgsConstructor
public class DemoSeeder implements CommandLineRunner {

    private static final int WORKSPACE_ID = 1;

    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;

    @Override
    public void run(String... args) {
        if (channelRepository.findCanonicalByWorkspaceId(WORKSPACE_ID).isPresent()) {
            return;
        }

        Instant now = Instant.now();
        Channel channel = channelRepository.save(Channel.builder()
                .type(ChannelType.GROUP)
                .workspaceId(WORKSPACE_ID)
                .name("general")
                .canonical(true)
                .members(new ArrayList<>(List.of(1, 2, 3)))
                .createdBy(1)
                .createdAt(now)
                .updatedAt(now)
                .build());

        seedMessage(channel.getId(), 1, "Welcome to the demo office! 👋", now.minusSeconds(180));
        seedMessage(channel.getId(), 2, "Morning! Grabbing coffee then standup.", now.minusSeconds(90));
        seedMessage(channel.getId(), 3, "Heading to the meeting room now.", now.minusSeconds(20));

        log.info("seeded demo chat channel {} for workspace {}", channel.getId(), WORKSPACE_ID);
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
