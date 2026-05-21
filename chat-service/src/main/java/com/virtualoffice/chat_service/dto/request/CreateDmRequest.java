package com.virtualoffice.chat_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDmRequest {

    @NotNull(message = "targetUserId is required")
    private Integer targetUserId;
}
