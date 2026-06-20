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

import com.virtualoffice.room_service.dto.response.ParticipantResponse;

import java.util.List;

public interface PresenceService {

    ParticipantResponse join(String roomId, Integer userId, int maxParticipants);

    boolean leave(String roomId, Integer userId);

    ParticipantResponse updateState(String roomId, Integer userId, boolean muted, boolean cameraOn, boolean screenSharing);

    void heartbeat(String roomId, Integer userId);

    List<ParticipantResponse> listParticipants(String roomId);

    void clearRoom(String roomId);
}
