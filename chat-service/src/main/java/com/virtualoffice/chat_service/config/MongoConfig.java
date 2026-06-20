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
package com.virtualoffice.chat_service.config;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;

// declares the partial-unique indexes that Spring's @CompoundIndex can't express:
// `sparse: true` still indexes documents whose field is present-but-null, but Spring
// always writes nulls, so we filter on `$type` to exclude both missing and null values.
// (`$ne: null` would be more natural but isn't on MongoDB's partialFilterExpression allow-list.)
@Slf4j
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
        createOrReplaceIndex(
                mongoTemplate.getCollection("messages"),
                Indexes.compoundIndex(
                        Indexes.ascending("senderId"),
                        Indexes.ascending("clientMessageId")
                ),
                new IndexOptions()
                        .name("idx_sender_clientMsgId")
                        .unique(true)
                        .partialFilterExpression(Filters.type("clientMessageId", BsonType.STRING))
        );
    }

    private void ensureChannelNameUniqueness() {
        createOrReplaceIndex(
                mongoTemplate.getCollection("channels"),
                Indexes.compoundIndex(
                        Indexes.ascending("workspaceId"),
                        Indexes.ascending("name")
                ),
                new IndexOptions()
                        .name("idx_workspace_name")
                        .unique(true)
                        .partialFilterExpression(Filters.and(
                                Filters.type("workspaceId", BsonType.INT32),
                                Filters.eq("type", "GROUP")
                        ))
        );
    }

    private void ensureDmKeyUniqueness() {
        createOrReplaceIndex(
                mongoTemplate.getCollection("channels"),
                Indexes.ascending("dmKey"),
                new IndexOptions()
                        .name("idx_dmKey")
                        .unique(true)
                        .partialFilterExpression(Filters.type("dmKey", BsonType.STRING))
        );
    }

    // makes index setup idempotent across spec changes — earlier versions of these indexes
    // (e.g. `partialFilterExpression: { $exists: true }`) live on in older data volumes and
    // would otherwise block startup with IndexOptionsConflict / IndexKeySpecsConflict
    private void createOrReplaceIndex(MongoCollection<Document> coll, Bson keys, IndexOptions options) {
        try {
            coll.createIndex(keys, options);
        } catch (MongoCommandException e) {
            int code = e.getErrorCode();
            if (code == 85 || code == 86) {
                log.info("dropping stale index {} on {} ({})", options.getName(), coll.getNamespace(), e.getErrorCodeName());
                coll.dropIndex(options.getName());
                coll.createIndex(keys, options);
            } else {
                throw e;
            }
        }
    }
}
