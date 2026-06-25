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
package com.virtualoffice.chat_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * {@link WorkspaceClient} backed by an HTTP call to workspace-service's internal API, carrying the
 * shared {@code X-Internal-Token}. Maps 404 (no active desk) to {@link Optional#empty()}; surfaces
 * an unreachable workspace-service as 503 so callers fail closed rather than silently allowing.
 */
@Component
public class WorkspaceClientImpl implements WorkspaceClient {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceClientImpl.class);
    private static final String MEMBER_ROLE_PATH = "/api/internal/workspace/{workspaceId}/members/{userId}/role";
    static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final RestClient restClient;

    public WorkspaceClientImpl(RestClient.Builder builder,
                               @Value("${workspace.service.base-url}") String baseUrl,
                               @Value("${workspace.service.internal-token}") String internalToken) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HEADER_INTERNAL_TOKEN, internalToken)
                .build();
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

    /** Internal signal that the role endpoint returned 404 (no active desk). */
    private static final class NotAMemberException extends RuntimeException {
    }
}
