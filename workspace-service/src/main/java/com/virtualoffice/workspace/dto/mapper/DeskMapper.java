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
package com.virtualoffice.workspace.dto.mapper;

import com.virtualoffice.workspace.dto.response.DeskResponse;
import com.virtualoffice.workspace.dto.response.DeskWidgetResponse;
import com.virtualoffice.workspace.model.Desk;
import com.virtualoffice.workspace.model.DeskWidget;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DeskMapper {

    DeskWidgetResponse toWidget(DeskWidget widget);

    List<DeskWidgetResponse> toWidgets(List<DeskWidget> widgets);

    // links + widgets come from their own child tables, so they're passed in as extra sources.
    // boolean is-fields expose properties "online"/"active"; record components are "isOnline"/"isActive".
    @Mapping(target = "links", source = "links")
    @Mapping(target = "widgets", source = "widgets")
    @Mapping(target = "isOnline", source = "desk.online")
    @Mapping(target = "isActive", source = "desk.active")
    DeskResponse toResponse(Desk desk, List<String> links, List<DeskWidgetResponse> widgets);
}
