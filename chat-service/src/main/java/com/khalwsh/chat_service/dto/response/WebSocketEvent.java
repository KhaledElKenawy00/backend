package com.khalwsh.chat_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// every WS message goes out as { action, payload }
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketEvent<T> {

    private String action;
    private T payload;

    public static final String NEW_MESSAGE = "NEW_MESSAGE";
    public static final String EDIT_MESSAGE = "EDIT_MESSAGE";
    public static final String DELETE_MESSAGE = "DELETE_MESSAGE";
    public static final String TYPING = "TYPING";
    public static final String THREAD_DELETED = "THREAD_DELETED";

    public static <T> WebSocketEvent<T> of(String action, T payload) {
        return WebSocketEvent.<T>builder()
                .action(action)
                .payload(payload)
                .build();
    }
}
