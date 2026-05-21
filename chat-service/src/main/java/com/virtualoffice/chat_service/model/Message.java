package com.virtualoffice.chat_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "idx_channel_created", def = "{'channelId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "idx_thread_created", def = "{'threadId': 1, 'createdAt': -1}")
})
public class Message {

    @Id
    private ObjectId id;

    private ObjectId channelId;

    private Integer senderId;

    // captured at send time so the admin-vs-admin delete rule still works after a role change
    private String senderRole;
    private String content;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    private ObjectId threadId;

    private ObjectId replyToId;

    private List<Integer> mentions;

    private String clientMessageId;

    @Builder.Default
    private Boolean deleted = false;

    private Instant deletedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
