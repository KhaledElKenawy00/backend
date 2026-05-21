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
package com.virtualoffice.chat_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// every WS message goes out as { action, payload }
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketEvent<T> {

    private String action;
    private T payload;

    public static final String NEW_MESSAGE = "NEW_MESSAGE";
    public static final String EDIT_MESSAGE = "EDIT_MESSAGE";
    public static final String DELETE_MESSAGE = "DELETE_MESSAGE";
    public static final String TYPING = "TYPING";
    public static final String THREAD_DELETED = "THREAD_DELETED";

    public static <T> WebSocketEvent<T> of(String action, T payload) {
        return WebSocketEvent.<T>builder()
                .action(action)
                .payload(payload)
                .build();
    }
}
