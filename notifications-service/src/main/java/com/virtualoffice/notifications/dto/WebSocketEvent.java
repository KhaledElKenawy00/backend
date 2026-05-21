package com.virtualoffice.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WebSocketEvent {
    private String action;
    private Object payload;
}
