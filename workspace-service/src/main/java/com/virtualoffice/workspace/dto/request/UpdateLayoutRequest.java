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

import com.virtualoffice.workspace.dto.LayoutLayer;
import com.virtualoffice.workspace.dto.LayoutSpawnPoint;
import com.virtualoffice.workspace.dto.LayoutTileset;
import com.virtualoffice.workspace.dto.LayoutZone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Full-replace layout edit. {@code expectedVersion} must match the workspace's current
 * {@code layoutVersion} (optimistic lock) — a mismatch is a 409 so concurrent admins
 * don't clobber each other. Each list REPLACES the corresponding set wholesale.
 */
public record UpdateLayoutRequest(
        @NotNull Long expectedVersion,
        @NotNull @Min(1) Integer tileSize,
        @NotNull @Min(1) Integer mapWidth,
        @NotNull @Min(1) Integer mapHeight,
        String mapGeometry,
        @Valid List<LayoutTileset> tilesets,
        @Valid List<LayoutLayer> layers,
        @Valid List<LayoutZone> zones,
        @Valid List<LayoutSpawnPoint> spawnPoints) {
}
