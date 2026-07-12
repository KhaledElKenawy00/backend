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
package com.virtualoffice.chat_service.service.impl;

import com.virtualoffice.chat_service.client.NotificationsPushClient;
import com.virtualoffice.chat_service.client.WorkspaceClient;
import com.virtualoffice.chat_service.client.WorkspaceRole;
import com.virtualoffice.chat_service.dto.mapper.DtoMapper;
import com.virtualoffice.chat_service.dto.request.CreateChannelRequest;
import com.virtualoffice.chat_service.dto.response.ChannelResponse;
import com.virtualoffice.chat_service.dto.response.PaginatedResponse;
import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChannelType;
import com.virtualoffice.chat_service.repository.ChannelRepository;
import com.virtualoffice.chat_service.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;
    private final WorkspaceClient workspaceClient;
    private final NotificationsPushClient notificationsPushClient;

    @Override
    public ChannelResponse createGroupChannel(CreateChannelRequest request, Integer creatorUserId) {
        if (request.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceId is required for group channels");
        }

        // Workspace-scoped authorization (Design.md §chat-service): creating a channel requires at
        // least MEMBER in the target workspace. Role lives in workspace-service, queried on demand.
        workspaceClient.requireRole(request.getWorkspaceId(), creatorUserId, WorkspaceRole.MEMBER);

        LinkedHashSet<Integer> memberSet = new LinkedHashSet<>(request.getMembers());
        memberSet.removeIf(Objects::isNull);
        memberSet.add(creatorUserId);
        List<Integer> members = new ArrayList<>(memberSet);

        Instant now = Instant.now();

        Channel channel = Channel.builder()
                .name(request.getName())
                .type(ChannelType.GROUP)
                .workspaceId(request.getWorkspaceId())
                .members(members)
                .createdBy(creatorUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Channel saved = channelRepository.save(channel);

        // Push real-time MEMBERSHIP_UPDATED to every non-creator member so their
        // app refreshes immediately without waiting for the polling interval.
        members.stream()
                .filter(uid -> !uid.equals(creatorUserId))
                .forEach(uid -> notificationsPushClient.pushMembershipUpdated(
                        uid, "CHANNEL", request.getWorkspaceId()));

        return DtoMapper.toChannelResponse(saved);
    }

    @Override
    public PaginatedResponse<ChannelResponse> getWorkspaceChannels(Integer workspaceId, Integer userId, int page, int limit) {
        // API is 1-based, Spring's PageRequest is 0-based
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.ASC, "name"));

        Page<Channel> channelPage = channelRepository.findWorkspaceChannelsForUser(workspaceId, userId, pageRequest);

        List<ChannelResponse> channels = channelPage.getContent()
                .stream()
                .map(DtoMapper::toChannelResponse)
                .toList();

        return PaginatedResponse.<ChannelResponse>builder()
                .content(channels)
                .totalPages(channelPage.getTotalPages())
                .totalElements(channelPage.getTotalElements())
                .currentPage(page)
                .build();
    }

    @Override
    public PaginatedResponse<ChannelResponse> getRoomChannels(Integer workspaceId, Integer userId, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<Channel> channelPage = channelRepository.findRoomChannelsForUser(workspaceId, userId, pageRequest);

        List<ChannelResponse> channels = channelPage.getContent()
                .stream()
                .map(DtoMapper::toChannelResponse)
                .toList();

        return PaginatedResponse.<ChannelResponse>builder()
                .content(channels)
                .totalPages(channelPage.getTotalPages())
                .totalElements(channelPage.getTotalElements())
                .currentPage(page)
                .build();
    }

    @Override
    public ChannelResponse getChannel(String channelId) {
        ObjectId id = new ObjectId(channelId);
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "channel not found"));
        return DtoMapper.toChannelResponse(channel);
    }

    @Override
    public void joinChannel(String channelId, Integer userId) {
        ObjectId id = new ObjectId(channelId);
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "channel not found"));

        if (channel.getType() != ChannelType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only group channels can be joined");
        }

        if (channel.getMembers().contains(userId)) {
            return;
        }

        channelRepository.addMember(id, userId, Instant.now());
    }

    @Override
    public void leaveChannel(String channelId, Integer userId) {
        ObjectId id = new ObjectId(channelId);
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "channel not found"));

        if (channel.getType() != ChannelType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only group channels can be left");
        }

        if (!channel.getMembers().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a member of this channel");
        }

        channelRepository.removeMember(id, userId, Instant.now());
        channelRepository.deleteIfEmpty(id);
    }

    @Override
    public ChannelResponse getOrCreateDm(Integer currentUserId, Integer targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot create a DM with yourself");
        }

        // canonical key — smaller id first so the lookup is order-independent
        String dmKey = Math.min(currentUserId, targetUserId) + "_" + Math.max(currentUserId, targetUserId);

        Optional<Channel> existing = channelRepository.findByDmKey(dmKey);
        if (existing.isPresent()) {
            return DtoMapper.toChannelResponse(existing.get());
        }

        Instant now = Instant.now();
        Channel dm = Channel.builder()
                .type(ChannelType.DIRECT)
                .workspaceId(null)
                .members(List.of(currentUserId, targetUserId))
                .dmKey(dmKey)
                .createdBy(currentUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            return DtoMapper.toChannelResponse(channelRepository.save(dm));
        } catch (DuplicateKeyException e) {
            // concurrent create lost the race — re-fetch the winner
            return channelRepository.findByDmKey(dmKey)
                    .map(DtoMapper::toChannelResponse)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DM creation failed unexpectedly"));
        }
    }

    @Override
    public PaginatedResponse<ChannelResponse> getDirectMessages(Integer userId, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<Channel> dmPage = channelRepository.findDirectChannelsForUser(ChannelType.DIRECT, userId, pageRequest);

        List<ChannelResponse> dms = dmPage.getContent()
                .stream()
                .map(DtoMapper::toChannelResponse)
                .toList();

        return PaginatedResponse.<ChannelResponse>builder()
                .content(dms)
                .totalPages(dmPage.getTotalPages())
                .totalElements(dmPage.getTotalElements())
                .currentPage(page)
                .build();
    }

    @Override
    public boolean isMember(String channelId, Integer userId) {
        ObjectId id = new ObjectId(channelId);
        Channel channel = channelRepository.findById(id).orElse(null);
        if (channel == null) return false;
        return channel.getMembers().contains(userId);
    }
}
