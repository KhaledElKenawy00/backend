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
