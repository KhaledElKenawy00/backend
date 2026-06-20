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

import com.virtualoffice.workspace.AbstractIntegrationTest;
import com.virtualoffice.workspace.Fixtures;
import com.virtualoffice.workspace.model.DeskWidget;
import com.virtualoffice.workspace.model.MapLayer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the @JdbcTypeCode(JSON) String <-> jsonb mapping round-trips against real Postgres.
@Transactional
class JsonbMappingIT extends AbstractIntegrationTest {

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    DeskRepository deskRepository;

    @Autowired
    MapLayerRepository mapLayerRepository;

    @Autowired
    DeskWidgetRepository deskWidgetRepository;

    @Test
    void mapLayerDataRoundTrips() {
        Long wid = workspaceRepository.save(Fixtures.workspace("json-" + System.nanoTime()).build()).getId();
        MapLayer saved = mapLayerRepository.saveAndFlush(MapLayer.builder()
                .workspaceId(wid).name("Ground").layerIndex(0).collides(false)
                .data("[[1,2],[3,4]]").build());

        MapLayer found = mapLayerRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getData()).isEqualTo("[[1,2],[3,4]]");
    }

    @Test
    void deskWidgetConfigRoundTrips() {
        Long wid = workspaceRepository.save(Fixtures.workspace("json-" + System.nanoTime()).build()).getId();
        Long deskId = deskRepository.save(Fixtures.desk(wid, 1L).build()).getId();
        DeskWidget saved = deskWidgetRepository.saveAndFlush(DeskWidget.builder()
                .deskId(deskId).type("CLOCK").label("My clock").position(0)
                .config("{\"tz\":\"UTC\"}").build());

        DeskWidget found = deskWidgetRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getConfig()).contains("tz");
    }
}
