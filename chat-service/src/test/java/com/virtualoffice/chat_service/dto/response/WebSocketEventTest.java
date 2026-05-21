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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketEventTest {

    // ────────────────────────────────────────
    // factory method
    // ────────────────────────────────────────

    @Nested
    class OfFactory {

        @Test
        void shouldCreateEventWithActionAndPayload() {
            WebSocketEvent<String> event = WebSocketEvent.of("NEW_MESSAGE", "hello");

            assertThat(event.getAction()).isEqualTo("NEW_MESSAGE");
            assertThat(event.getPayload()).isEqualTo("hello");
        }

        @Test
        void shouldCreateEventWithMapPayload() {
            WebSocketEvent<Map<String, String>> event = WebSocketEvent.of("ERROR",
                    Map.of("code", "INTERNAL_ERROR", "message", "oops"));

            assertThat(event.getAction()).isEqualTo("ERROR");
            assertThat(event.getPayload().get("code")).isEqualTo("INTERNAL_ERROR");
        }

        @Test
        void shouldHandleNullPayload() {
            WebSocketEvent<String> event = WebSocketEvent.of("DELETE_MESSAGE", null);

            assertThat(event.getAction()).isEqualTo("DELETE_MESSAGE");
            assertThat(event.getPayload()).isNull();
        }
    }

    // ────────────────────────────────────────
    // constants
    // ────────────────────────────────────────

    @Nested
    class Constants {

        @Test
        void shouldHaveCorrectActionConstants() {
            assertThat(WebSocketEvent.NEW_MESSAGE).isEqualTo("NEW_MESSAGE");
            assertThat(WebSocketEvent.EDIT_MESSAGE).isEqualTo("EDIT_MESSAGE");
            assertThat(WebSocketEvent.DELETE_MESSAGE).isEqualTo("DELETE_MESSAGE");
            assertThat(WebSocketEvent.TYPING).isEqualTo("TYPING");
            assertThat(WebSocketEvent.THREAD_DELETED).isEqualTo("THREAD_DELETED");
        }
    }

    // ────────────────────────────────────────
    // builder and setters (lombok)
    // ────────────────────────────────────────

    @Nested
    class BuilderAndSetters {

        @Test
        void shouldBuildWithBuilder() {
            WebSocketEvent<String> event = WebSocketEvent.<String>builder()
                    .action("TYPING")
                    .payload("data")
                    .build();

            assertThat(event.getAction()).isEqualTo("TYPING");
            assertThat(event.getPayload()).isEqualTo("data");
        }

        @Test
        void shouldSupportNoArgsConstructor() {
            WebSocketEvent<String> event = new WebSocketEvent<>();
            event.setAction("TEST");
            event.setPayload("value");

            assertThat(event.getAction()).isEqualTo("TEST");
            assertThat(event.getPayload()).isEqualTo("value");
        }

        @Test
        void shouldSupportAllArgsConstructor() {
            WebSocketEvent<Integer> event = new WebSocketEvent<>("COUNT", 42);

            assertThat(event.getAction()).isEqualTo("COUNT");
            assertThat(event.getPayload()).isEqualTo(42);
        }
    }
}
