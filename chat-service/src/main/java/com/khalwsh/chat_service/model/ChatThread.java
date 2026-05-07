package com.khalwsh.chat_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "threads")
@CompoundIndexes({
    @CompoundIndex(name = "idx_rootMessageId", def = "{'rootMessageId': 1}", unique = true)
})
public class ChatThread {

    @Id
    private ObjectId id;

    @Indexed
    private ObjectId channelId;

    private ObjectId rootMessageId;

    private String name;

    private Integer createdBy;

    // captured at creation time so the admin-vs-admin delete rule still works after a role change
    private String creatorRole;

    @Builder.Default
    private Boolean deleted = false;

    private Instant createdAt;

    private Instant updatedAt;
}
