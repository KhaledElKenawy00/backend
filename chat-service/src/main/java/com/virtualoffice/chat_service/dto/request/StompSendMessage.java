package com.virtualoffice.chat_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StompSendMessage {

    private String channelId;
    private String content;
    private String threadId;
    private String replyToId;
    private List<Integer> mentions;
    private String clientMessageId;
}
