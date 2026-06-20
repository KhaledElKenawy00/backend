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
package com.virtualoffice.room_service.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomChannelEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${room.exchange}")
    private String exchange;

    @Value("${room.channel.routing-key}")
    private String routingKey;

    public void publishCreate(String channelId, Integer workspaceId, String name, List<Integer> members) {
        publish(RoomChannelEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RoomChannelEventType.ROOM_CHANNEL_CREATE)
                .channelId(channelId)
                .workspaceId(workspaceId)
                .name(name)
                .members(members)
                .build());
    }

    public void publishDelete(String channelId) {
        publish(RoomChannelEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RoomChannelEventType.ROOM_CHANNEL_DELETE)
                .channelId(channelId)
                .build());
    }

    public void publishAddMember(String channelId, Integer userId) {
        publish(RoomChannelEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RoomChannelEventType.ROOM_CHANNEL_ADD_MEMBER)
                .channelId(channelId)
                .userId(userId)
                .build());
    }

    public void publishRemoveMember(String channelId, Integer userId) {
        publish(RoomChannelEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RoomChannelEventType.ROOM_CHANNEL_REMOVE_MEMBER)
                .channelId(channelId)
                .userId(userId)
                .build());
    }

    private void publish(RoomChannelEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
