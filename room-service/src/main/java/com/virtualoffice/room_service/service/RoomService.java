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
package com.virtualoffice.room_service.service;

import com.virtualoffice.room_service.dto.request.CreateRoomRequest;
import com.virtualoffice.room_service.dto.request.UpdateRoomRequest;
import com.virtualoffice.room_service.dto.response.PaginatedResponse;
import com.virtualoffice.room_service.dto.response.RoomResponse;

public interface RoomService {

    RoomResponse createRoom(CreateRoomRequest request, Integer creatorUserId);

    PaginatedResponse<RoomResponse> getRooms(Integer workspaceId, Integer userId, int page, int limit);

    RoomResponse getRoom(String roomId, Integer userId);

    RoomResponse updateRoom(String roomId, UpdateRoomRequest request, Integer userId);

    void deleteRoom(String roomId, Integer userId);

    void addMember(String roomId, Integer targetUserId, Integer requesterUserId);

    void removeMember(String roomId, Integer targetUserId, Integer requesterUserId);

    boolean isMember(String roomId, Integer userId);
}
