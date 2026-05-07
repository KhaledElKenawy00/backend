package com.khalwsh.chat_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingNotification {

    private Integer userId;
    private String channelId;
    private String threadId;
    private boolean typing;
}
