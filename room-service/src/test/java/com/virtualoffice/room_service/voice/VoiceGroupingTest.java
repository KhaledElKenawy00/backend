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
package com.virtualoffice.room_service.voice;

import com.virtualoffice.room_service.client.Zone;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceGroupingTest {

    private static final long WID = 7;
    private static final int DEFAULT_RADIUS = 100;

    private static Zone meetingRoom(String voiceRoomId, int x, int y, int w, int h) {
        return new Zone(1L, "MEETING_ROOM", "Room", x, y, w, h, voiceRoomId, null);
    }

    private static Zone open(Integer radius, int x, int y, int w, int h) {
        return new Zone(2L, "OPEN", "Floor", x, y, w, h, null, radius);
    }

    private static Map<Integer, int[]> positions(Object... pairs) {
        Map<Integer, int[]> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 3) {
            map.put((Integer) pairs[i], new int[]{(Integer) pairs[i + 1], (Integer) pairs[i + 2]});
        }
        return map;
    }

    @Test
    void avatarInsideZoneJoinsThatZonesVoiceChannel() {
        List<Zone> zones = List.of(meetingRoom("voice-A", 0, 0, 100, 100));
        Map<Integer, String> result = VoiceGrouping.assign(WID, zones, positions(10, 50, 50), DEFAULT_RADIUS);

        assertThat(result).containsEntry(10, "voice-A");
    }

    @Test
    void avatarsOutsideAnyZoneClusterByProximity() {
        // 10 and 20 are 30 apart (within 100); 30 is far away.
        Map<Integer, String> result = VoiceGrouping.assign(WID, List.of(),
                positions(10, 0, 0, 20, 30, 0, 30, 1000, 1000), DEFAULT_RADIUS);

        assertThat(result).containsEntry(10, "prox-7-10").containsEntry(20, "prox-7-10");
        assertThat(result).doesNotContainKey(30); // lone avatar joins no channel
    }

    @Test
    void proximityIsTransitiveViaConnectedComponents() {
        // chain: 10-20 within range, 20-30 within range, 10-30 NOT directly within range
        Map<Integer, String> result = VoiceGrouping.assign(WID, List.of(),
                positions(10, 0, 0, 20, 90, 0, 30, 180, 0), DEFAULT_RADIUS);

        assertThat(result.get(10)).isEqualTo("prox-7-10");
        assertThat(result.get(20)).isEqualTo("prox-7-10");
        assertThat(result.get(30)).isEqualTo("prox-7-10");
    }

    @Test
    void channelNameUsesSmallestUserIdRegardlessOfOrder() {
        Map<Integer, String> result = VoiceGrouping.assign(WID, List.of(),
                positions(30, 0, 0, 5, 10, 0), DEFAULT_RADIUS);

        assertThat(result.values()).containsOnly("prox-7-5");
    }

    @Test
    void zoneVoiceTakesPrecedenceOverProximity() {
        // Both avatars are adjacent, but 10 is inside the meeting room → zone voice, not proximity.
        List<Zone> zones = List.of(meetingRoom("voice-A", 0, 0, 50, 50));
        Map<Integer, String> result = VoiceGrouping.assign(WID, zones,
                positions(10, 10, 10, 20, 60, 10), DEFAULT_RADIUS);

        assertThat(result).containsEntry(10, "voice-A");
        assertThat(result).doesNotContainKey(20); // 20 is outside, alone → no channel
    }

    @Test
    void openZoneRadiusOverridesDefault() {
        // A tight 20-unit open zone keeps avatars 30 apart in separate groups despite the larger default.
        List<Zone> zones = List.of(open(20, 0, 0, 1000, 1000));
        Map<Integer, String> result = VoiceGrouping.assign(WID, zones,
                positions(10, 0, 0, 20, 30, 0), DEFAULT_RADIUS);

        assertThat(result).isEmpty();
    }

    @Test
    void emptyPositionsProduceNoAssignments() {
        assertThat(VoiceGrouping.assign(WID, List.of(), Map.of(), DEFAULT_RADIUS)).isEmpty();
    }
}
