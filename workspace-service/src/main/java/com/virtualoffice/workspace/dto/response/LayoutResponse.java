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
package com.virtualoffice.workspace.dto.response;

import com.virtualoffice.workspace.dto.LayoutLayer;
import com.virtualoffice.workspace.dto.LayoutSpawnPoint;
import com.virtualoffice.workspace.dto.LayoutTileset;
import com.virtualoffice.workspace.dto.LayoutZone;

import java.util.List;

/**
 * The full floorplan assembled from the normalized tables (workspace + tileset + map_layer
 * + zone + spawn_point). The SkyOffice client builds its Phaser tilemap from this (D2).
 * {@code layoutVersion} is the optimistic-lock token the client echoes back on update.
 */
public record LayoutResponse(
        Long workspaceId,
        Integer tileSize,
        Integer mapWidth,
        Integer mapHeight,
        String mapGeometry,
        Long layoutVersion,
        List<LayoutTileset> tilesets,
        List<LayoutLayer> layers,
        List<LayoutZone> zones,
        List<LayoutSpawnPoint> spawnPoints) {
}
