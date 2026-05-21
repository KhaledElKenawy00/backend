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

import com.virtualoffice.chat_service.dto.request.CreateChannelRequest;
import com.virtualoffice.chat_service.dto.request.CreateDmRequest;
import com.virtualoffice.chat_service.dto.response.ChannelResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.service.ChannelService;
import com.virtualoffice.chat_service.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelControllerTest {

    @Mock
    private ChannelService channelService;

    @InjectMocks
    private ChannelController controller;

    private HttpServletRequest mockRequest(String userId, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    // ────────────────────────────────────────
    // createChannel
    // ────────────────────────────────────────

    @Nested
    class CreateChannel {

        @Test
        void shouldReturn201OnSuccess() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            CreateChannelRequest req = CreateChannelRequest.builder()
                    .name("general").workspaceId(1).members(List.of(10, 20)).build();
            ChannelResponse expected = ChannelResponse.builder().id("ch1").name("general").build();
            when(channelService.createGroupChannel(any(), eq(10))).thenReturn(expected);

            ResponseEntity<ChannelResponse> response = controller.createChannel(req, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldReturn409OnDuplicateKey() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            CreateChannelRequest req = CreateChannelRequest.builder()
                    .name("general").workspaceId(1).members(List.of(10)).build();
            when(channelService.createGroupChannel(any(), eq(10))).thenThrow(new DuplicateKeyException("dup"));

            ResponseEntity<ChannelResponse> response = controller.createChannel(req, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ────────────────────────────────────────
    // getChannels
    // ────────────────────────────────────────

    @Nested
    class GetChannels {

        @Test
        void shouldReturnPaginatedChannels() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            PaginatedResponse<ChannelResponse> expected = PaginatedResponse.<ChannelResponse>builder()
                    .content(List.of()).currentPage(1).totalElements(0).totalPages(0).build();
            when(channelService.getWorkspaceChannels(1, 10, 1, 20)).thenReturn(expected);

            ResponseEntity<PaginatedResponse<ChannelResponse>> response =
                    controller.getChannels(1, 1, 20, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }
    }

    // ────────────────────────────────────────
    // getChannel — membership check
    // ────────────────────────────────────────

    @Nested
    class GetChannel {

        @Test
        void shouldReturnChannelForMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(true);
            ChannelResponse expected = ChannelResponse.builder().id("ch1").name("general").build();
            when(channelService.getChannel("ch1")).thenReturn(expected);

            ResponseEntity<ChannelResponse> response = controller.getChannel("ch1", httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        void shouldThrow403ForNonMember() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            when(channelService.isMember("ch1", 10)).thenReturn(false);

            assertThatThrownBy(() -> controller.getChannel("ch1", httpRequest))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    });
        }
    }

    // ────────────────────────────────────────
    // joinChannel
    // ────────────────────────────────────────

    @Nested
    class JoinChannel {

        @Test
        void shouldReturn200() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");

            ResponseEntity<Void> response = controller.joinChannel("ch1", httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(channelService).joinChannel("ch1", 10);
        }
    }

    // ────────────────────────────────────────
    // leaveChannel
    // ────────────────────────────────────────

    @Nested
    class LeaveChannel {

        @Test
        void shouldReturn200() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");

            ResponseEntity<Void> response = controller.leaveChannel("ch1", httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(channelService).leaveChannel("ch1", 10);
        }
    }

    // ────────────────────────────────────────
    // getOrCreateDm
    // ────────────────────────────────────────

    @Nested
    class GetOrCreateDm {

        @Test
        void shouldReturnDm() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            CreateDmRequest req = new CreateDmRequest();
            req.setTargetUserId(20);
            ChannelResponse expected = ChannelResponse.builder().id("dm1").type("DIRECT").build();
            when(channelService.getOrCreateDm(10, 20)).thenReturn(expected);

            ResponseEntity<ChannelResponse> response = controller.getOrCreateDm(req, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }
    }

    // ────────────────────────────────────────
    // getDirectMessages
    // ────────────────────────────────────────

    @Nested
    class GetDirectMessages {

        @Test
        void shouldReturnPaginatedDms() {
            HttpServletRequest httpRequest = mockRequest("10", "USER");
            PaginatedResponse<ChannelResponse> expected = PaginatedResponse.<ChannelResponse>builder()
                    .content(List.of()).currentPage(1).totalElements(0).totalPages(0).build();
            when(channelService.getDirectMessages(10, 1, 20)).thenReturn(expected);

            ResponseEntity<PaginatedResponse<ChannelResponse>> response =
                    controller.getDirectMessages(1, 20, httpRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }
    }
}
