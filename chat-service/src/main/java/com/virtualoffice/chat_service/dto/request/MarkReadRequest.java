package com.virtualoffice.chat_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadRequest {

    @NotBlank(message = "lastReadMessageId is required")
    private String lastReadMessageId;
}
