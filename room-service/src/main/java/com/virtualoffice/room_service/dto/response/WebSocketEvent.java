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
package com.virtualoffice.room_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketEvent<T> {

    private String action;
    private T payload;

    public static final String PARTICIPANT_JOINED = "PARTICIPANT_JOINED";
    public static final String PARTICIPANT_LEFT = "PARTICIPANT_LEFT";
    public static final String STATE_CHANGED = "STATE_CHANGED";
    public static final String ROOM_UPDATED = "ROOM_UPDATED";
    public static final String ROOM_CLOSED = "ROOM_CLOSED";

    public static <T> WebSocketEvent<T> of(String action, T payload) {
        return WebSocketEvent.<T>builder()
                .action(action)
                .payload(payload)
                .build();
    }
}
