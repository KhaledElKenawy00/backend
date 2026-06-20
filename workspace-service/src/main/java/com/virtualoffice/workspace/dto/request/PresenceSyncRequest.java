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
package com.virtualoffice.workspace.dto.request;

import com.virtualoffice.workspace.model.enums.DeskStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Colyseus -> workspace-service presence sync. {@code status}/{@code statusEmoji}/position are
 * optional; position should only be sent throttled (e.g. on leave or periodically), never per frame.
 */
public record PresenceSyncRequest(
        @NotNull Long userId,
        @NotNull Boolean isOnline,
        DeskStatus status,
        String statusEmoji,
        Integer positionX,
        Integer positionY) {
}
