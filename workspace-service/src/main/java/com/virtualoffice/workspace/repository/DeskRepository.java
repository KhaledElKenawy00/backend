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
package com.virtualoffice.workspace.repository;

import com.virtualoffice.workspace.model.Desk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeskRepository extends JpaRepository<Desk, Long> {

    Optional<Desk> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    Optional<Desk> findByIdAndWorkspaceId(Long id, Long workspaceId);

    // membership guard — used by the access guard on every workspace-scoped request
    boolean existsByWorkspaceIdAndUserIdAndIsActiveTrue(Long workspaceId, Long userId);

    // member directory (active only), paginated
    Page<Desk> findByWorkspaceIdAndIsActiveTrue(Long workspaceId, Pageable pageable);

    List<Desk> findByWorkspaceIdAndIsActiveTrue(Long workspaceId);

    // workspaces a user actively belongs to (GET /api/workspace/mine)
    List<Desk> findByUserIdAndIsActiveTrue(Long userId);

    Optional<Desk> findByWorkspaceIdAndInviteStatusAndWorkEmail(
            Long workspaceId, com.virtualoffice.workspace.model.enums.InviteStatus inviteStatus, String workEmail);
}
