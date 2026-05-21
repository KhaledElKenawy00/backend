package com.virtualoffice.chat_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private String id;
    private String channelId;
    private Integer senderId;
    private String content;
    private String type;
    private String threadId;
    private String replyToId;
    private List<Integer> mentions;
    private String clientMessageId;
    private Boolean deleted;
    private Instant deletedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
