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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class WorkspaceRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    WorkspaceRepository repository;

    @Test
    void savesAndFindsBySlugWithJsonbGeometry() {
        Workspace saved = repository.save(Fixtures.workspace("acme")
                .mapGeometry("{\"background\":\"day\"}")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLayoutVersion()).isZero();        // @Version initialized
        assertThat(saved.getCreatedAt()).isNotNull();         // @CreationTimestamp

        Workspace found = repository.findBySlug("acme").orElseThrow();
        assertThat(found.getMapGeometry()).contains("background");
    }

    @Test
    void slugIsUnique() {
        repository.saveAndFlush(Fixtures.workspace("dup").build());
        assertThatThrownBy(() -> repository.saveAndFlush(Fixtures.workspace("dup").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsBySlugReflectsState() {
        assertThat(repository.existsBySlug("ghost")).isFalse();
        repository.saveAndFlush(Fixtures.workspace("ghost").build());
        assertThat(repository.existsBySlug("ghost")).isTrue();
    }
}
