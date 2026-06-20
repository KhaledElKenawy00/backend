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
package com.virtualoffice.room_service.config;

import com.virtualoffice.room_service.service.WebSocketTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketHandshakeInterceptorTest {

    @Mock
    private WebSocketTicketService ticketService;

    @Mock
    private ServletServerHttpRequest servletRequest;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    @Test
    void shouldBindUserIdWhenTicketValid() {
        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor(ticketService);
        when(servletRequest.getServletRequest()).thenReturn(httpRequest);
        when(httpRequest.getParameter("ticket")).thenReturn("abc");
        when(ticketService.validateAndConsumeTicket("abc")).thenReturn(10);

        Map<String, Object> attributes = new HashMap<>();
        boolean ok = interceptor.beforeHandshake(servletRequest, response, wsHandler, attributes);

        assertThat(ok).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(10);
    }

    @Test
    void shouldRejectWhenTicketInvalid() {
        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor(ticketService);
        when(servletRequest.getServletRequest()).thenReturn(httpRequest);
        when(httpRequest.getParameter("ticket")).thenReturn("bad");
        when(ticketService.validateAndConsumeTicket("bad")).thenReturn(null);

        Map<String, Object> attributes = new HashMap<>();
        boolean ok = interceptor.beforeHandshake(servletRequest, response, wsHandler, attributes);

        assertThat(ok).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    void shouldRejectNonServletRequest() {
        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor(ticketService);
        ServerHttpRequest nonServlet = mock(ServerHttpRequest.class);

        boolean ok = interceptor.beforeHandshake(nonServlet, response, wsHandler, new HashMap<>());

        assertThat(ok).isFalse();
        verifyNoInteractions(ticketService);
    }
}
