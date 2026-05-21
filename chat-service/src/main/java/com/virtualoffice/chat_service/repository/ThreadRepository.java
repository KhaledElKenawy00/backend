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
package com.virtualoffice.chat_service.repository;

import com.virtualoffice.chat_service.model.ChatThread;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThreadRepository extends MongoRepository<ChatThread, ObjectId> {

    @Query("{ 'channelId': ?0, 'deleted': false }")
    Page<ChatThread> findActiveThreadsByChannelId(ObjectId channelId, Pageable pageable);

    @Query(value = "{ 'rootMessageId': ?0 }", exists = true)
    boolean existsByRootMessageId(ObjectId rootMessageId);

    @Query("{ '_id': ?0, 'deleted': false }")
    Optional<ChatThread> findActiveById(ObjectId threadId);
}
