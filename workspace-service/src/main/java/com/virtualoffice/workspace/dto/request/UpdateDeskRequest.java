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

import com.virtualoffice.workspace.model.enums.AvatarCharacter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partial update of the caller's own desk. Scalar null fields are left unchanged.
 * A non-null {@code links}/{@code widgets} list REPLACES the existing set (send the full set).
 */
public record UpdateDeskRequest(
        @Size(max = 120) String fullName,
        @Size(max = 60) String nickName,
        @Size(max = 120) String title,
        @Size(max = 1000) String bio,
        AvatarCharacter avatarCharacter,
        String timezone,
        Long teamId,
        List<@Size(max = 500) String> links,
        @Valid List<WidgetInput> widgets) {

    public record WidgetInput(
            @NotBlank @Size(max = 40) String type,
            @Size(max = 120) String label,
            @NotNull Integer position,
            String config) {
    }
}
