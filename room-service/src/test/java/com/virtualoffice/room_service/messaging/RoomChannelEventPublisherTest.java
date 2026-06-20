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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoomChannelEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RoomChannelEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "exchange", "room.exchange");
        ReflectionTestUtils.setField(publisher, "routingKey", "room.channel.event");
    }

    private RoomChannelEvent capturePublished() {
        ArgumentCaptor<RoomChannelEvent> captor = ArgumentCaptor.forClass(RoomChannelEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("room.exchange"), eq("room.channel.event"), captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldPublishCreate() {
        publisher.publishCreate("c1", 7, "Standup", List.of(1, 2));

        RoomChannelEvent event = capturePublished();
        assertThat(event.getType()).isEqualTo(RoomChannelEventType.ROOM_CHANNEL_CREATE);
        assertThat(event.getChannelId()).isEqualTo("c1");
        assertThat(event.getWorkspaceId()).isEqualTo(7);
        assertThat(event.getName()).isEqualTo("Standup");
        assertThat(event.getMembers()).containsExactly(1, 2);
        assertThat(event.getEventId()).isNotBlank();
    }

    @Test
    void shouldPublishDelete() {
        publisher.publishDelete("c1");

        RoomChannelEvent event = capturePublished();
        assertThat(event.getType()).isEqualTo(RoomChannelEventType.ROOM_CHANNEL_DELETE);
        assertThat(event.getChannelId()).isEqualTo("c1");
    }

    @Test
    void shouldPublishAddMember() {
        publisher.publishAddMember("c1", 42);

        RoomChannelEvent event = capturePublished();
        assertThat(event.getType()).isEqualTo(RoomChannelEventType.ROOM_CHANNEL_ADD_MEMBER);
        assertThat(event.getChannelId()).isEqualTo("c1");
        assertThat(event.getUserId()).isEqualTo(42);
    }

    @Test
    void shouldPublishRemoveMember() {
        publisher.publishRemoveMember("c1", 42);

        RoomChannelEvent event = capturePublished();
        assertThat(event.getType()).isEqualTo(RoomChannelEventType.ROOM_CHANNEL_REMOVE_MEMBER);
        assertThat(event.getChannelId()).isEqualTo("c1");
        assertThat(event.getUserId()).isEqualTo(42);
    }
}
