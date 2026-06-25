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
package com.virtualoffice.workspace.messaging;

import java.util.List;

/**
 * Event emitted on workspace membership changes so chat-service can provision and
 * sync the canonical per-workspace chat channel (INTEGRATION.md §5.1). workspace-service
 * stores no messages; this is the only chat coupling.
 *
 * <p>The channel is keyed by {@code workspaceId} (not a chat-side id) — chat-service
 * find-or-creates its own channel document. {@code members} is populated only on
 * {@code WORKSPACE_CHANNEL_CREATE}; {@code userId} carries the single subject for
 * add/remove. {@code eventId} is for idempotency/tracing.
 */
public record WorkspaceChannelEvent(
        String eventId,
        WorkspaceChannelEventType type,
        Long workspaceId,
        String name,
        Long userId,
        List<Long> members) {
}
