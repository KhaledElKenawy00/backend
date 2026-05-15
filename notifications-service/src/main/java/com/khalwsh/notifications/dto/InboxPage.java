package com.khalwsh.notifications.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InboxPage {
    private List<NotificationResponse> items;
    private int page;
    private int size;
    private long total;
    private long unreadCount;
}
