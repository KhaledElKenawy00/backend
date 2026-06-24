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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure voice-grouping policy (INTEGRATION.md §4.3): given the workspace zones and a snapshot of
 * avatar positions, decide which Agora channel each avatar should be in.
 *
 * <ul>
 *   <li><b>Zone voice</b> — an avatar inside a zone that has a {@code voiceRoomId} joins that
 *       private channel; only occupants connect.</li>
 *   <li><b>Proximity voice</b> — avatars in open areas within hearing range of one another form a
 *       group via connected components; each group of two or more gets a deterministic ad-hoc
 *       channel ({@code prox-<workspaceId>-<smallest userId>}). A lone avatar is in no channel.</li>
 * </ul>
 *
 * Stateless and deterministic: the same inputs always yield the same channel names, so callers can
 * diff successive results to detect who moved between channels.
 */
public final class VoiceGrouping {

    private VoiceGrouping() {
    }

    /**
     * @param positions userId → {@code [x, y]}
     * @return userId → Agora channel, for every avatar that is in a channel (lone avatars omitted)
     */
    public static Map<Integer, String> assign(long workspaceId, List<Zone> zones,
                                               Map<Integer, int[]> positions, int defaultRadius) {
        Map<Integer, String> result = new LinkedHashMap<>();
        // userId → effective proximity radius, for avatars not captured by a zone voice channel
        Map<Integer, Integer> openRadius = new LinkedHashMap<>();

        for (Map.Entry<Integer, int[]> e : positions.entrySet()) {
            Zone zone = zoneAt(zones, e.getValue()[0], e.getValue()[1]);
            if (zone != null && zone.hasVoiceRoom()) {
                result.put(e.getKey(), zone.voiceRoomId());
            } else {
                int radius = (zone != null && zone.proximityRadius() != null) ? zone.proximityRadius() : defaultRadius;
                openRadius.put(e.getKey(), radius);
            }
        }

        clusterByProximity(workspaceId, positions, openRadius, result);
        return result;
    }

    /** The zone the point sits in; prefers a zone with a voice room when several overlap. */
    private static Zone zoneAt(List<Zone> zones, int x, int y) {
        if (zones == null) {
            return null;
        }
        Zone fallback = null;
        for (Zone zone : zones) {
            if (zone.contains(x, y)) {
                if (zone.hasVoiceRoom()) {
                    return zone;
                }
                if (fallback == null) {
                    fallback = zone;
                }
            }
        }
        return fallback;
    }

    private static void clusterByProximity(long workspaceId, Map<Integer, int[]> positions,
                                           Map<Integer, Integer> openRadius, Map<Integer, String> result) {
        List<Integer> open = new ArrayList<>(openRadius.keySet());
        UnionFind uf = new UnionFind(open);

        for (int i = 0; i < open.size(); i++) {
            for (int j = i + 1; j < open.size(); j++) {
                int a = open.get(i);
                int b = open.get(j);
                // Both must be within the other's hearing range (symmetric grouping).
                int reach = Math.min(openRadius.get(a), openRadius.get(b));
                if (withinSquared(positions.get(a), positions.get(b), (long) reach * reach)) {
                    uf.union(a, b);
                }
            }
        }

        Map<Integer, List<Integer>> components = new HashMap<>();
        for (Integer user : open) {
            components.computeIfAbsent(uf.find(user), k -> new ArrayList<>()).add(user);
        }
        for (List<Integer> members : components.values()) {
            if (members.size() < 2) {
                continue; // a lone avatar joins no channel
            }
            int smallest = members.stream().min(Integer::compareTo).orElseThrow();
            String channel = "prox-" + workspaceId + "-" + smallest;
            for (Integer user : members) {
                result.put(user, channel);
            }
        }
    }

    private static boolean withinSquared(int[] a, int[] b, long maxDistSquared) {
        long dx = a[0] - b[0];
        long dy = a[1] - b[1];
        return dx * dx + dy * dy <= maxDistSquared;
    }

    /** Minimal union-find over a fixed set of user ids. */
    private static final class UnionFind {
        private final Map<Integer, Integer> parent = new HashMap<>();

        UnionFind(List<Integer> ids) {
            for (Integer id : ids) {
                parent.put(id, id);
            }
        }

        int find(int x) {
            int root = x;
            while (parent.get(root) != root) {
                root = parent.get(root);
            }
            while (parent.get(x) != root) {
                int next = parent.get(x);
                parent.put(x, root);
                x = next;
            }
            return root;
        }

        void union(int a, int b) {
            parent.put(find(a), find(b));
        }
    }
}
