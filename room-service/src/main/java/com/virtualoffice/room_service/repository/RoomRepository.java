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
package com.virtualoffice.room_service.repository;

import com.virtualoffice.room_service.model.Room;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface RoomRepository extends MongoRepository<Room, ObjectId> {

    @Query("{ 'workspaceId': ?0, 'members': ?1 }")
    Page<Room> findByWorkspaceIdAndMember(Integer workspaceId, Integer userId, Pageable pageable);

    @Query("{ 'workspaceId': ?0 }")
    Page<Room> findByWorkspaceId(Integer workspaceId, Pageable pageable);

    @Query("{ '_id': ?0 }")
    @Update("{ '$addToSet': { 'members': ?1 }, '$set': { 'updatedAt': ?2 } }")
    long addMember(ObjectId roomId, Integer userId, Instant updatedAt);

    @Query("{ '_id': ?0 }")
    @Update("{ '$pull': { 'members': ?1 }, '$set': { 'updatedAt': ?2 } }")
    long removeMember(ObjectId roomId, Integer userId, Instant updatedAt);
}
