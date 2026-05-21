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
package com.virtualoffice.chat_service.config;

import com.virtualoffice.chat_service.service.WebSocketTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketHandshakeInterceptorTest {

    @Mock
    private WebSocketTicketService webSocketTicketService;

    @InjectMocks
    private WebSocketHandshakeInterceptor interceptor;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        attributes = new HashMap<>();
    }

    // ────────────────────────────────────────
    // beforeHandshake
    // ────────────────────────────────────────

    @Nested
    class BeforeHandshake {

        @Test
        void shouldAcceptValidTicket() {
            ServletServerHttpRequest servletRequest = mock(ServletServerHttpRequest.class);
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            when(servletRequest.getServletRequest()).thenReturn(httpRequest);
            when(httpRequest.getParameter("ticket")).thenReturn("valid-ticket");
            when(webSocketTicketService.validateAndConsumeTicket("valid-ticket"))
                    .thenReturn(Map.of("userId", 10, "userRole", "USER"));

            boolean result = interceptor.beforeHandshake(servletRequest, response, wsHandler, attributes);

            assertThat(result).isTrue();
            assertThat(attributes.get("userId")).isEqualTo(10);
            assertThat(attributes.get("userRole")).isEqualTo("USER");
        }

        @Test
        void shouldStoreAdminRole() {
            ServletServerHttpRequest servletRequest = mock(ServletServerHttpRequest.class);
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            when(servletRequest.getServletRequest()).thenReturn(httpRequest);
            when(httpRequest.getParameter("ticket")).thenReturn("admin-ticket");
            when(webSocketTicketService.validateAndConsumeTicket("admin-ticket"))
                    .thenReturn(Map.of("userId", 42, "userRole", "ADMIN"));

            interceptor.beforeHandshake(servletRequest, response, wsHandler, attributes);

            assertThat(attributes.get("userId")).isEqualTo(42);
            assertThat(attributes.get("userRole")).isEqualTo("ADMIN");
        }

        @Test
        void shouldRejectInvalidTicket() {
            ServletServerHttpRequest servletRequest = mock(ServletServerHttpRequest.class);
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            when(servletRequest.getServletRequest()).thenReturn(httpRequest);
            when(httpRequest.getParameter("ticket")).thenReturn("bad-ticket");
            when(webSocketTicketService.validateAndConsumeTicket("bad-ticket")).thenReturn(null);

            boolean result = interceptor.beforeHandshake(servletRequest, response, wsHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).doesNotContainKey("userId");
        }

        @Test
        void shouldRejectNullTicket() {
            ServletServerHttpRequest servletRequest = mock(ServletServerHttpRequest.class);
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            when(servletRequest.getServletRequest()).thenReturn(httpRequest);
            when(httpRequest.getParameter("ticket")).thenReturn(null);
            when(webSocketTicketService.validateAndConsumeTicket(null)).thenReturn(null);

            boolean result = interceptor.beforeHandshake(servletRequest, response, wsHandler, attributes);

            assertThat(result).isFalse();
        }

        @Test
        void shouldRejectNonServletRequest() {
            // non-ServletServerHttpRequest (e.g. Netty request) not supported
            ServerHttpRequest genericRequest = mock(ServerHttpRequest.class);

            boolean result = interceptor.beforeHandshake(genericRequest, response, wsHandler, attributes);

            assertThat(result).isFalse();
            verifyNoInteractions(webSocketTicketService);
        }
    }

    // ────────────────────────────────────────
    // afterHandshake — no-op, just verify it doesn't blow up
    // ────────────────────────────────────────

    @Nested
    class AfterHandshake {

        @Test
        void shouldDoNothing() {
            ServletServerHttpRequest servletRequest = mock(ServletServerHttpRequest.class);

            // should not throw
            interceptor.afterHandshake(servletRequest, response, wsHandler, null);
        }

        @Test
        void shouldHandleExceptionGracefully() {
            ServletServerHttpRequest servletRequest = mock(ServletServerHttpRequest.class);

            interceptor.afterHandshake(servletRequest, response, wsHandler, new RuntimeException("test"));
        }
    }
}
