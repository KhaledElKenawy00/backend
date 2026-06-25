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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link WorkspaceClient} backed by an HTTP call to workspace-service's internal API, carrying the
 * shared {@code X-Internal-Token}. Maps 404 (no active desk) to {@link Optional#empty()}; surfaces
 * an unreachable workspace-service as 503 so callers fail closed rather than silently allowing.
 * Zone reads are cached for a short TTL so a high-frequency position feed does not hammer
 * workspace-service.
 */
@Component
public class WorkspaceClientImpl implements WorkspaceClient {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceClientImpl.class);
    private static final String MEMBER_ROLE_PATH = "/api/internal/workspace/{workspaceId}/members/{userId}/role";
    private static final String ZONES_PATH = "/api/internal/workspace/{workspaceId}/zones";
    private static final ParameterizedTypeReference<List<Zone>> ZONE_LIST = new ParameterizedTypeReference<>() {
    };
    static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final RestClient restClient;
    private final long zonesTtlNanos;
    private final Map<Integer, CachedZones> zonesCache = new ConcurrentHashMap<>();

    public WorkspaceClientImpl(RestClient.Builder builder,
                               @Value("${workspace.service.base-url}") String baseUrl,
                               @Value("${workspace.service.internal-token}") String internalToken,
                               @Value("${workspace.service.zones-cache-ttl-seconds:30}") long zonesTtlSeconds) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HEADER_INTERNAL_TOKEN, internalToken)
                .build();
        this.zonesTtlNanos = Duration.ofSeconds(zonesTtlSeconds).toNanos();
    }

    @Override
    public Optional<WorkspaceMemberRole> getMemberRole(int workspaceId, int userId) {
        try {
            WorkspaceMemberRole role = restClient.get()
                    .uri(MEMBER_ROLE_PATH, workspaceId, userId)
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                            (req, res) -> { throw new NotAMemberException(); })
                    .body(WorkspaceMemberRole.class);
            return Optional.ofNullable(role);
        } catch (NotAMemberException e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("workspace-service role lookup failed for workspace {} user {}: {}",
                    workspaceId, userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "workspace-service unavailable");
        }
    }

    @Override
    public void requireRole(int workspaceId, int userId, WorkspaceRole minRole) {
        WorkspaceMemberRole member = getMemberRole(workspaceId, userId)
                .filter(WorkspaceMemberRole::active)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "not an active member of this workspace"));

        if (member.role() == null || !member.role().isAtLeast(minRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "requires " + minRole + " role in this workspace");
        }
    }

    @Override
    public List<Zone> getZones(int workspaceId) {
        CachedZones cached = zonesCache.get(workspaceId);
        if (cached != null && !cached.isExpired()) {
            return cached.zones();
        }
        try {
            List<Zone> zones = restClient.get()
                    .uri(ZONES_PATH, workspaceId)
                    .retrieve()
                    .body(ZONE_LIST);
            List<Zone> result = zones != null ? List.copyOf(zones) : List.of();
            zonesCache.put(workspaceId, new CachedZones(result, System.nanoTime() + zonesTtlNanos));
            return result;
        } catch (RestClientException e) {
            log.error("workspace-service zones lookup failed for workspace {}: {}", workspaceId, e.getMessage());
            // Serve stale zones if we have them rather than dropping voice grouping entirely.
            if (cached != null) {
                return cached.zones();
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "workspace-service unavailable");
        }
    }

    /** Internal signal that the role endpoint returned 404 (no active desk). */
    private static final class NotAMemberException extends RuntimeException {
    }

    private record CachedZones(List<Zone> zones, long expiryNanos) {
        boolean isExpired() {
            return System.nanoTime() - expiryNanos >= 0;
        }
    }
}
