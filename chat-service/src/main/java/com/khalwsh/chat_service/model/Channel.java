package com.khalwsh.chat_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

// dmKey uniqueness lives in MongoConfig — sparse: true would still index the explicit `null`
// that Spring writes for GROUP channels, so a partialFilterExpression is the only correct option
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "channels")
public class Channel {

    @Id
    private ObjectId id;

    private String name;

    private ChannelType type;

    private Integer workspaceId;

    @Indexed
    private List<Integer> members;

    private String dmKey;

    private Integer createdBy;

    private Instant createdAt;

    private Instant updatedAt;
}
