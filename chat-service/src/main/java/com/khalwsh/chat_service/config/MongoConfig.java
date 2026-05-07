package com.khalwsh.chat_service.config;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;

// declares the partial-unique indexes that Spring's @CompoundIndex can't express:
// `sparse: true` still indexes documents whose field is present-but-null, but Spring
// always writes nulls, so we have to filter on `$exists: true, $ne: null` instead.
@Configuration
@EnableMongoAuditing
@RequiredArgsConstructor
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    void ensureIndexes() {
        ensureIdempotencyIndex();
        ensureChannelNameUniqueness();
        ensureDmKeyUniqueness();
    }

    private void ensureIdempotencyIndex() {
        var messages = mongoTemplate.getCollection("messages");
        messages.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("senderId"),
                        Indexes.ascending("clientMessageId")
                ),
                new IndexOptions()
                        .name("idx_sender_clientMsgId")
                        .unique(true)
                        .partialFilterExpression(
                                Filters.and(
                                        Filters.exists("clientMessageId"),
                                        Filters.ne("clientMessageId", null)
                                )
                        )
        );
    }

    private void ensureChannelNameUniqueness() {
        var channels = mongoTemplate.getCollection("channels");
        channels.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("workspaceId"),
                        Indexes.ascending("name")
                ),
                new IndexOptions()
                        .name("idx_workspace_name")
                        .unique(true)
                        .partialFilterExpression(
                                Filters.and(
                                        Filters.exists("workspaceId"),
                                        Filters.ne("workspaceId", null)
                                )
                        )
        );
    }

    private void ensureDmKeyUniqueness() {
        var channels = mongoTemplate.getCollection("channels");
        channels.createIndex(
                Indexes.ascending("dmKey"),
                new IndexOptions()
                        .name("idx_dmKey")
                        .unique(true)
                        .partialFilterExpression(
                                Filters.and(
                                        Filters.exists("dmKey"),
                                        Filters.ne("dmKey", null)
                                )
                        )
        );
    }
}
