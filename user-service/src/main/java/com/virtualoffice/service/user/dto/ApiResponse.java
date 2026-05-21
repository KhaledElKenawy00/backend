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
package com.virtualoffice.service.user.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class ApiResponse {

    private String status;
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public ApiResponse(String message) {
        this.status = message;
    }

    public ApiResponse add(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    @JsonAnyGetter // Used to flatten the map
    public Map<String, Object> getFields() {
        return fields;
    }
}
