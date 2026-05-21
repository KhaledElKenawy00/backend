package com.virtualoffice.chat_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadResponse {

    private String id;
    private String channelId;
    private String rootMessageId;
    private String name;
    private Integer createdBy;
    private Boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
}
