package com.virtualoffice.chat_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StompTypingEvent {

    private String channelId;
    private String threadId;
    private boolean typing;
}
