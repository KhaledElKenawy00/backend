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
public class ChannelResponse {

    private String id;
    private String name;
    private String type;
    private Integer workspaceId;
    private List<Integer> members;
    private Integer createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
