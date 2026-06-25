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
package com.virtualoffice.room_service.client;

/**
 * A spatial zone owned by workspace-service (GET …/zones), the geometry that drives voice grouping
 * (INTEGRATION.md §4.3). {@code voiceRoomId} marks a private zone voice channel (e.g. a meeting
 * room); when null the zone is an open area where {@code proximityRadius} governs ad-hoc grouping.
 * {@code type} is the workspace-side enum name (MEETING_ROOM, OPEN, …), kept as a string here.
 */
public record Zone(
        Long id,
        String type,
        String name,
        Integer x,
        Integer y,
        Integer width,
        Integer height,
        String voiceRoomId,
        Integer proximityRadius) {

    /** True if the point falls inside this zone's bounds (half-open on the far edges). */
    public boolean contains(int px, int py) {
        if (x == null || y == null || width == null || height == null) {
            return false;
        }
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean hasVoiceRoom() {
        return voiceRoomId != null && !voiceRoomId.isBlank();
    }
}
