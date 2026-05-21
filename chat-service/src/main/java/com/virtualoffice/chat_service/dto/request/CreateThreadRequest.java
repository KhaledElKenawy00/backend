package com.virtualoffice.chat_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateThreadRequest {

    @NotBlank(message = "rootMessageId is required")
    private String rootMessageId;

    @NotBlank(message = "thread name is required")
    @Size(max = 100, message = "thread name must be 100 characters or less")
    private String name;
}
