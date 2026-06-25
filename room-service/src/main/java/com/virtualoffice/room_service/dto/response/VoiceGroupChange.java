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
package com.virtualoffice.room_service.dto.response;

import java.util.List;

/**
 * Tells one avatar which Agora voice channel to be in after a position update (INTEGRATION.md §4.3).
 * Broadcast as the payload of a {@code VOICE_GROUP_CHANGED} event. A null {@code channel} means the
 * avatar should leave voice (became a lone proximity singleton). {@code peers} are the other members
 * of the same channel, which the client can use for distance-based volume falloff.
 */
public record VoiceGroupChange(
        Integer userId,
        String channel,
        List<Integer> peers) {
}
