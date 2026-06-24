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
package com.virtualoffice.room_service.seed;

import com.virtualoffice.room_service.model.Room;
import com.virtualoffice.room_service.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds a demo voice room for the seeded demo workspace (id 1), with members that line up with the
 * workspace-service desks. Enabled only with {@code demo.seed=true} (dev/run.sh sets it) and
 * idempotent — it does nothing if the workspace already has a room.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true")
@RequiredArgsConstructor
public class DemoSeeder implements CommandLineRunner {

    private static final int WORKSPACE_ID = 1;

    private final RoomRepository roomRepository;

    @Override
    public void run(String... args) {
        if (roomRepository.findByWorkspaceId(WORKSPACE_ID, PageRequest.of(0, 1)).hasContent()) {
            return;
        }

        ObjectId roomId = new ObjectId();
        Instant now = Instant.now();
        roomRepository.save(Room.builder()
                .id(roomId)
                .workspaceId(WORKSPACE_ID)
                .name("Lounge")
                .channelId(new ObjectId().toHexString())
                .agoraChannelName("room-" + roomId.toHexString())
                .members(new ArrayList<>(List.of(1, 2)))
                .maxParticipants(25)
                .createdBy(1)
                .createdAt(now)
                .updatedAt(now)
                .build());

        log.info("seeded demo voice room {} for workspace {}", roomId.toHexString(), WORKSPACE_ID);
    }
}
