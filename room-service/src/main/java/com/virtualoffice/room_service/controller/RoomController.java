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
package com.virtualoffice.room_service.controller;

import com.virtualoffice.room_service.dto.request.AddMemberRequest;
import com.virtualoffice.room_service.dto.request.CreateRoomRequest;
import com.virtualoffice.room_service.dto.request.UpdateRoomRequest;
import com.virtualoffice.room_service.dto.response.PaginatedResponse;
import com.virtualoffice.room_service.dto.response.RoomResponse;
import com.virtualoffice.room_service.service.PresenceService;
import com.virtualoffice.room_service.service.RoomPushService;
import com.virtualoffice.room_service.service.RoomService;
import com.virtualoffice.room_service.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomPushService roomPushService;
    private final PresenceService presenceService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        RoomResponse response = roomService.createRoom(request, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<RoomResponse>> getRooms(
            @RequestParam Integer workspaceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        return ResponseEntity.ok(roomService.getRooms(workspaceId, user.getUserId(), page, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoom(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        return ResponseEntity.ok(roomService.getRoom(id, user.getUserId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable String id,
            @Valid @RequestBody UpdateRoomRequest request,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        RoomResponse response = roomService.updateRoom(id, request, user.getUserId());
        roomPushService.roomUpdated(id, response);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        roomService.deleteRoom(id, user.getUserId());
        presenceService.clearRoom(id);
        roomPushService.roomClosed(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable String id,
            @Valid @RequestBody AddMemberRequest request,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        roomService.addMember(id, request.getUserId(), user.getUserId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String id,
            @PathVariable Integer userId,
            HttpServletRequest httpRequest) {

        UserContext.UserInfo user = UserContext.fromRequest(httpRequest);
        roomService.removeMember(id, userId, user.getUserId());
        return ResponseEntity.ok().build();
    }
}
