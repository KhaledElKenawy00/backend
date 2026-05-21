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

import com.virtualoffice.chat_service.model.Message;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends MongoRepository<Message, ObjectId> {

    @Query("{ 'channelId': ?0, 'threadId': null }")
    Page<Message> findChannelMessages(ObjectId channelId, Pageable pageable);

    @Query("{ 'channelId': ?0, 'threadId': null, '_id': { $lt: ?1 } }")
    List<Message> findChannelMessagesBefore(ObjectId channelId, ObjectId beforeId, Pageable pageable);

    @Query("{ 'channelId': ?0, 'threadId': null, '_id': { $gt: ?1 } }")
    List<Message> findChannelMessagesAfter(ObjectId channelId, ObjectId afterId, Pageable pageable);

    @Query("{ 'threadId': ?0 }")
    Page<Message> findThreadMessages(ObjectId threadId, Pageable pageable);

    @Query("{ 'threadId': ?0, '_id': { $lt: ?1 } }")
    List<Message> findThreadMessagesBefore(ObjectId threadId, ObjectId beforeId, Pageable pageable);

    @Query("{ 'threadId': ?0, '_id': { $gt: ?1 } }")
    List<Message> findThreadMessagesAfter(ObjectId threadId, ObjectId afterId, Pageable pageable);

    @Query("{ 'senderId': ?0, 'clientMessageId': ?1 }")
    Optional<Message> findBySenderIdAndClientMessageId(Integer senderId, String clientMessageId);

    @Query(value = "{ 'channelId': ?0, 'threadId': null, '_id': { $gt: ?1 } }", count = true)
    long countChannelMessagesAfter(ObjectId channelId, ObjectId afterMessageId);

    @Query(value = "{ 'threadId': ?0, '_id': { $gt: ?1 } }", count = true)
    long countThreadMessagesAfter(ObjectId threadId, ObjectId afterMessageId);

    @Query(value = "{ 'channelId': ?0, 'threadId': null }", count = true)
    long countChannelMessages(ObjectId channelId);

    @Query(value = "{ 'threadId': ?0 }", count = true)
    long countThreadMessages(ObjectId threadId);

    @Query("{ 'threadId': ?0 }")
    List<Message> findAllByThreadId(ObjectId threadId);
}
