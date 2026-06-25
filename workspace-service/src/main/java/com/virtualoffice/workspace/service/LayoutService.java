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
package com.virtualoffice.workspace.service;

import com.virtualoffice.workspace.dto.request.UpdateLayoutRequest;
import com.virtualoffice.workspace.dto.response.LayoutResponse;

public interface LayoutService {

    LayoutResponse getLayout(Long workspaceId, Long requesterId);

    LayoutResponse updateLayout(Long workspaceId, UpdateLayoutRequest request, Long requesterId);

    // Unguarded assembly for server-to-server callers (Colyseus session boot); no membership check.
    LayoutResponse getLayoutInternal(Long workspaceId);
}
