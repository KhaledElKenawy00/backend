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
package com.virtualoffice.chat_service.controller;

import com.virtualoffice.chat_service.dto.request.MarkReadRequest;
import com.virtualoffice.chat_service.dto.response.ThreadResponse;
import com.virtualoffice.chat_service.dto.response.UnreadCountResponse;
import com.virtualoffice.chat_service.service.ChannelService;
import com.virtualoffice.chat_service.service.ReadReceiptService;
import com.virtualoffice.chat_service.service.ThreadService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadReceiptControllerTest {

    @Mock
    private ReadReceiptService readReceiptService;

    @Mock
    private ChannelService channelService;

    @Mock
    private ThreadService threadService;

    @InjectMocks
    private ReadReceiptController controller;

    private HttpServletRequest mockRequest(String userId, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    // ────────────────────────────────────────
    // markAsRead — channel
    // ────────────────────────────────────────

    @Nested
    class MarkAsRead {

        @Test
        void shouldMarkForMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            MarkReadRequest req = new MarkReadRequest();
            req.setLastReadMessageId("msg1");

            ResponseEntity<Void> response = controller.markAsRead("ch1", req, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(readReceiptService).markAsRead("ch1", 10, "msg1");
        }

        @Test
        void shouldThrow403ForNonMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(false);
            MarkReadRequest req = new MarkReadRequest();
            req.setLastReadMessageId("msg1");

            assertThatThrownBy(() -> controller.markAsRead("ch1", req, httpRequest))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ────────────────────────────────────────
    // getUnreadCount — channel
    // ────────────────────────────────────────

    @Nested
    class GetUnreadCount {

        @Test
        void shouldReturnUnreadForMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            UnreadCountResponse expected = UnreadCountResponse.builder()
                    .unreadCount(5).lastReadMessageId("msg1").build();
            when(readReceiptService.getUnreadCount("ch1", 10)).thenReturn(expected);

            ResponseEntity<UnreadCountResponse> response = controller.getUnreadCount("ch1", httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldThrow403ForNonMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(false);

            assertThatThrownBy(() -> controller.getUnreadCount("ch1", httpRequest))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ────────────────────────────────────────
    // markThreadAsRead — membership via parent channel
    // ────────────────────────────────────────

    @Nested
    class MarkThreadAsRead {

        @Test
        void shouldMarkForMemberOfParentChannel() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            MarkReadRequest req = new MarkReadRequest();
            req.setLastReadMessageId("msg1");

            ResponseEntity<Void> response = controller.markThreadAsRead("t1", req, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(readReceiptService).markThreadAsRead("t1", 10, "msg1");
        }

        @Test
        void shouldThrow403ForNonMemberOfParentChannel() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(false);
            MarkReadRequest req = new MarkReadRequest();
            req.setLastReadMessageId("msg1");

            assertThatThrownBy(() -> controller.markThreadAsRead("t1", req, httpRequest))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ────────────────────────────────────────
    // getThreadUnreadCount — membership via parent channel
    // ────────────────────────────────────────

    @Nested
    class GetThreadUnreadCount {

        @Test
        void shouldReturnUnreadForMemberOfParentChannel() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            UnreadCountResponse expected = UnreadCountResponse.builder()
                    .unreadCount(3).lastReadMessageId("msg2").build();
            when(readReceiptService.getThreadUnreadCount("t1", 10)).thenReturn(expected);

            ResponseEntity<UnreadCountResponse> response = controller.getThreadUnreadCount("t1", httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldThrow403ForNonMemberOfParentChannel() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            ThreadResponse thread = ThreadResponse.builder().id("t1").channelId("ch1").build();
            when(threadService.getThread("t1")).thenReturn(thread);
            when(channelService.isMember("ch1", 10)).thenReturn(false);

            assertThatThrownBy(() -> controller.getThreadUnreadCount("t1", httpRequest))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }
    }
}
