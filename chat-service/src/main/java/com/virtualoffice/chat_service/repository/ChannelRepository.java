package com.virtualoffice.chat_service.repository;

import com.virtualoffice.chat_service.model.Channel;
import com.virtualoffice.chat_service.model.ChannelType;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChannelRepository extends MongoRepository<Channel, ObjectId> {

    @Query("{ 'workspaceId': ?0, 'members': ?1 }")
    Page<Channel> findWorkspaceChannelsForUser(Integer workspaceId, Integer userId, Pageable pageable);

    @Query("{ 'dmKey': ?0 }")
    Optional<Channel> findByDmKey(String dmKey);

    @Query("{ 'type': ?0, 'members': ?1 }")
    Page<Channel> findDirectChannelsForUser(ChannelType type, Integer userId, Pageable pageable);
}
