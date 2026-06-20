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
import org.bson.types.ObjectId;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomChannelListener {

    private final ChannelRepository channelRepository;

    @RabbitListener(queues = "${room.channel.queue}")
    public void handle(RoomChannelEvent event) {
        if (event.getType() == null || event.getChannelId() == null || !ObjectId.isValid(event.getChannelId())) {
            log.warn("dropping malformed room-channel event: {}", event);
            return;
        }
        switch (event.getType()) {
            case ROOM_CHANNEL_CREATE -> createChannel(event);
            case ROOM_CHANNEL_DELETE -> deleteChannel(event);
            case ROOM_CHANNEL_ADD_MEMBER -> addMember(event);
            case ROOM_CHANNEL_REMOVE_MEMBER -> removeMember(event);
        }
    }

    private void createChannel(RoomChannelEvent event) {
        ObjectId channelId = new ObjectId(event.getChannelId());
        if (channelRepository.existsById(channelId)) {
            return;
        }

        List<Integer> members = event.getMembers() != null
                ? new ArrayList<>(event.getMembers())
                : new ArrayList<>();

        Instant now = Instant.now();
        Channel channel = Channel.builder()
                .id(channelId)
                .name(null)
                .type(ChannelType.ROOM)
                .workspaceId(event.getWorkspaceId())
                .members(members)
                .createdBy(event.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        channelRepository.save(channel);
        log.info("created ROOM channel {} for workspace {}", event.getChannelId(), event.getWorkspaceId());
    }

    private void deleteChannel(RoomChannelEvent event) {
        channelRepository.deleteById(new ObjectId(event.getChannelId()));
        log.info("deleted ROOM channel {}", event.getChannelId());
    }

    private void addMember(RoomChannelEvent event) {
        if (event.getUserId() == null) {
            log.warn("dropping ADD_MEMBER event with no userId for channel {}", event.getChannelId());
            return;
        }
        channelRepository.addMember(new ObjectId(event.getChannelId()), event.getUserId(), Instant.now());
    }

    private void removeMember(RoomChannelEvent event) {
        if (event.getUserId() == null) {
            log.warn("dropping REMOVE_MEMBER event with no userId for channel {}", event.getChannelId());
            return;
        }
        channelRepository.removeMember(new ObjectId(event.getChannelId()), event.getUserId(), Instant.now());
    }
}
