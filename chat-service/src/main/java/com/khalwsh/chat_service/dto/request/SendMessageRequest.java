package com.khalwsh.chat_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "content is required")
    private String content;

    private String threadId;
    private String replyToId;
    private List<Integer> mentions;

    // client-generated UUID — repeat sends with the same id are deduplicated server-side
    private String clientMessageId;
}
