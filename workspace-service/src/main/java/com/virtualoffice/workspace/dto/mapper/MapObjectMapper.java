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

import com.virtualoffice.workspace.dto.response.MapObjectResponse;
import com.virtualoffice.workspace.model.MapObject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MapObjectMapper {

    // boolean field isActive exposes property "active" via isActive(); record component is "isActive"
    @Mapping(target = "isActive", source = "active")
    MapObjectResponse toResponse(MapObject mapObject);

    List<MapObjectResponse> toResponseList(List<MapObject> mapObjects);
}
