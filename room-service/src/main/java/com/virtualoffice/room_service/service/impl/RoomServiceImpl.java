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
package com.virtualoffice.room_service.service.impl;

import com.virtualoffice.room_service.dto.mapper.RoomMapper;
import com.virtualoffice.room_service.dto.request.CreateRoomRequest;
import com.virtualoffice.room_service.dto.request.UpdateRoomRequest;
import com.virtualoffice.room_service.dto.response.PaginatedResponse;
import com.virtualoffice.room_service.dto.response.RoomResponse;
import com.virtualoffice.room_service.messaging.RoomChannelEventPublisher;
import com.virtualoffice.room_service.model.Room;
import com.virtualoffice.room_service.repository.RoomRepository;
import com.virtualoffice.room_service.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Value;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private static final int DEFAULT_MAX_PARTICIPANTS = 25;

    private final RoomRepository roomRepository;
    private final RoomChannelEventPublisher publisher;

    @Value("${room.agora.channel-prefix}")
    private String agoraChannelPrefix;

    @Override
    public RoomResponse createRoom(CreateRoomRequest request, Integer creatorUserId) {
        ObjectId roomId = new ObjectId();
        String channelId = new ObjectId().toHexString();
        String agoraChannelName = agoraChannelPrefix + roomId.toHexString();

        LinkedHashSet<Integer> memberSet = new LinkedHashSet<>();
        if (request.getMembers() != null) {
            memberSet.addAll(request.getMembers());
        }
        memberSet.removeIf(Objects::isNull);
        memberSet.add(creatorUserId);
        List<Integer> members = new ArrayList<>(memberSet);

        int maxParticipants = request.getMaxParticipants() != null
                ? request.getMaxParticipants()
                : DEFAULT_MAX_PARTICIPANTS;

        Instant now = Instant.now();
        Room room = Room.builder()
                .id(roomId)
                .workspaceId(request.getWorkspaceId())
                .name(request.getName())
                .channelId(channelId)
                .agoraChannelName(agoraChannelName)
                .members(members)
                .maxParticipants(maxParticipants)
                .createdBy(creatorUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            roomRepository.save(room);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a room with this name already exists in the workspace");
        }

        try {
            publisher.publishCreate(channelId, request.getWorkspaceId(), request.getName(), members);
        } catch (AmqpException e) {
            roomRepository.deleteById(roomId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "failed to provision room text channel");
        }

        return RoomMapper.toResponse(room);
    }

    @Override
    public PaginatedResponse<RoomResponse> getRooms(Integer workspaceId, Integer userId, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<Room> roomPage = roomRepository.findByWorkspaceId(workspaceId, pageRequest);

        List<RoomResponse> rooms = roomPage.getContent()
                .stream()
                .map(RoomMapper::toResponse)
                .toList();

        return PaginatedResponse.<RoomResponse>builder()
                .content(rooms)
                .totalPages(roomPage.getTotalPages())
                .totalElements(roomPage.getTotalElements())
                .currentPage(page)
                .build();
    }

    @Override
    public RoomResponse getRoom(String roomId, Integer userId) {
        return RoomMapper.toResponse(loadMemberRoom(roomId, userId));
    }

    @Override
    public RoomResponse updateRoom(String roomId, UpdateRoomRequest request, Integer userId) {
        Room room = loadMemberRoom(roomId, userId);

        if (request.getName() != null) {
            room.setName(request.getName());
        }
        if (request.getMaxParticipants() != null) {
            room.setMaxParticipants(request.getMaxParticipants());
        }
        room.setUpdatedAt(Instant.now());

        try {
            roomRepository.save(room);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a room with this name already exists in the workspace");
        }

        return RoomMapper.toResponse(room);
    }

    @Override
    public void deleteRoom(String roomId, Integer userId) {
        Room room = loadMemberRoom(roomId, userId);
        roomRepository.deleteById(room.getId());
        try {
            publisher.publishDelete(room.getChannelId());
        } catch (AmqpException e) {
            log.warn("failed to publish ROOM_CHANNEL_DELETE for channel {}: {}", room.getChannelId(), e.getMessage());
        }
    }

    @Override
    public void addMember(String roomId, Integer targetUserId, Integer requesterUserId) {
        Room room = loadMemberRoom(roomId, requesterUserId);
        roomRepository.addMember(room.getId(), targetUserId, Instant.now());
        try {
            publisher.publishAddMember(room.getChannelId(), targetUserId);
        } catch (AmqpException e) {
            log.warn("failed to publish ROOM_CHANNEL_ADD_MEMBER for channel {}: {}", room.getChannelId(), e.getMessage());
        }
    }

    @Override
    public void removeMember(String roomId, Integer targetUserId, Integer requesterUserId) {
        Room room = loadMemberRoom(roomId, requesterUserId);
        roomRepository.removeMember(room.getId(), targetUserId, Instant.now());
        try {
            publisher.publishRemoveMember(room.getChannelId(), targetUserId);
        } catch (AmqpException e) {
            log.warn("failed to publish ROOM_CHANNEL_REMOVE_MEMBER for channel {}: {}", room.getChannelId(), e.getMessage());
        }
    }

    @Override
    public boolean isMember(String roomId, Integer userId) {
        Room room = roomRepository.findById(new ObjectId(roomId)).orElse(null);
        return room != null && room.getMembers() != null && room.getMembers().contains(userId);
    }

    @Override
    public RoomResponse ensureMemberAndGet(String roomId, Integer userId) {
        Room room = roomRepository.findById(new ObjectId(roomId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "room not found"));
        if (room.getMembers() == null || !room.getMembers().contains(userId)) {
            roomRepository.addMember(room.getId(), userId, Instant.now());
            room = roomRepository.findById(new ObjectId(roomId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "room not found"));
        }
        return RoomMapper.toResponse(room);
    }

    private Room loadMemberRoom(String roomId, Integer userId) {
        Room room = roomRepository.findById(new ObjectId(roomId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "room not found"));
        if (room.getMembers() == null || !room.getMembers().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of this room");
        }
        return room;
    }
}
