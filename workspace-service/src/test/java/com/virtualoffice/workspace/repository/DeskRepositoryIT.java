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
import com.virtualoffice.workspace.model.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class DeskRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    DeskRepository deskRepository;

    private Long workspaceId() {
        Workspace ws = workspaceRepository.save(Fixtures.workspace("desk-" + System.nanoTime()).build());
        return ws.getId();
    }

    @Test
    void enforcesOneDeskPerUserPerWorkspace() {
        Long wid = workspaceId();
        deskRepository.saveAndFlush(Fixtures.desk(wid, 7L).build());
        assertThatThrownBy(() -> deskRepository.saveAndFlush(Fixtures.desk(wid, 7L).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void directoryReturnsActiveOnlyPaginated() {
        Long wid = workspaceId();
        deskRepository.save(Fixtures.desk(wid, 1L).build());
        deskRepository.save(Fixtures.desk(wid, 2L).build());
        deskRepository.save(Fixtures.desk(wid, 3L).isActive(false).build());

        var page = deskRepository.findByWorkspaceIdAndIsActiveTrue(wid, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void membershipGuardSeesActiveOnly() {
        Long wid = workspaceId();
        deskRepository.save(Fixtures.desk(wid, 9L).build());
        deskRepository.save(Fixtures.desk(wid, 10L).isActive(false).build());

        assertThat(deskRepository.existsByWorkspaceIdAndUserIdAndIsActiveTrue(wid, 9L)).isTrue();
        assertThat(deskRepository.existsByWorkspaceIdAndUserIdAndIsActiveTrue(wid, 10L)).isFalse();
    }
}
